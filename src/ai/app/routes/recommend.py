"""``POST /recommend/furniture`` route (UC-01-recommendation FR-1..FR-12).

Cross-validates a persisted space analysis + the user's preferred style
against a seeded furniture catalog and returns a ranked top-N per
category. Scoring is deterministic rule-based (see
:mod:`app.analyzers.recommender` for the weights + thresholds).

The handler is ``async def`` (FR-11 / AC-22) and offloads scoring to a
worker thread via :func:`asyncio.to_thread` for catalogs with more
than 500 items (the seeded catalog is ~24 items, well below that
threshold — the check is defensive against future catalog growth).
"""

from __future__ import annotations

import asyncio
import logging
import time
from datetime import datetime, timezone
from typing import List, Sequence

from fastapi import APIRouter, Request

from app.analyzers.recommender import (
    CATEGORY_KEYS,
    DetectedObject,
    FurnitureItem,
    RoomDims,
    rank,
    resolve_style,
)
from app.errors import (
    AnalyzerError,
    CatalogEmptyError,
    RecommendationFailedError,
)
from app.schemas_reco import (
    RecommendRequest,
    RecommendResponse,
    RecommendationItemModel,
    RecommendationsBlock,
    ScoreBreakdownModel,
)
from app.schemas import ErrorResponse


log = logging.getLogger("recommender")

# Inline-vs-offload cutoff for the scorer (FR-11).
_INLINE_CATALOG_LIMIT = 500


router = APIRouter()


@router.post(
    "/recommend/furniture",
    response_model=RecommendResponse,
    responses={
        422: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
)
async def recommend_furniture(
    request: Request,
    body: RecommendRequest,
) -> RecommendResponse:
    """Rank furniture items for the given room + preferred style.

    FR-1..FR-12 end-to-end. Handler is ``async def``; heavy work runs
    via :func:`asyncio.to_thread` when the catalog is large enough to
    matter (``len > 500`` — the seeded catalog stays well under that
    ceiling so the inline path is the default).
    """
    t0 = time.perf_counter()
    request.state.room_id = body.roomId

    log.info(
        "recommend start roomId=%s userId=%s preferredStyle=%s catalogSize=%d topN=%d",
        body.roomId,
        body.userId,
        body.preferredStyle,
        len(body.catalog),
        body.topNPerCategory,
    )

    # FR-12 — catalog empty is an explicit 422 with CATALOG_EMPTY code.
    if len(body.catalog) == 0:
        raise CatalogEmptyError("Catalog is empty; nothing to rank.")

    # FR-4 — resolve the effective style used for the scorer.
    resolved = resolve_style(
        body.preferredStyle,
        body.space.detectedStyle,
    )

    room = RoomDims(
        widthM=body.space.dimensions.widthM,
        lengthM=body.space.dimensions.lengthM,
        heightM=body.space.dimensions.heightM,
    )

    detected: List[DetectedObject] = [
        DetectedObject(
            type=d.type,
            bboxNorm=(d.bboxNorm[0], d.bboxNorm[1], d.bboxNorm[2], d.bboxNorm[3]),
            confidence=d.confidence,
        )
        for d in body.space.detectedObjects
    ]

    catalog: Sequence[FurnitureItem] = [
        FurnitureItem(
            furnitureId=c.furnitureId,
            type=c.type,
            name=c.name,
            styleTags=tuple(c.styleTags),
            widthCm=c.widthCm,
            lengthCm=c.lengthCm,
            heightCm=c.heightCm,
            colorHex=c.colorHex,
            price=c.price,
            imageUrl=c.imageUrl,
        )
        for c in body.catalog
    ]

    # ---- FR-11 — run the scorer inline for small catalogs, offload otherwise.
    try:
        if len(catalog) > _INLINE_CATALOG_LIMIT:
            buckets = await asyncio.to_thread(
                rank,
                catalog,
                resolved_style=resolved,
                room=room,
                room_color_hex=body.space.mainColor,
                detected_objects=detected,
                top_n_per_category=body.topNPerCategory,
            )
        else:
            buckets = rank(
                catalog,
                resolved_style=resolved,
                room=room,
                room_color_hex=body.space.mainColor,
                detected_objects=detected,
                top_n_per_category=body.topNPerCategory,
            )
    except AnalyzerError:
        # Already structured — let the module-level handler render it.
        raise
    except Exception as e:  # defensive
        log.exception("recommend scorer crashed roomId=%s", body.roomId)
        raise RecommendationFailedError(
            f"Scorer failed unexpectedly: {e.__class__.__name__}"
        ) from e

    # ---- FR-12 — NO_FIT_ANY_CATEGORY warning (still HTTP 200).
    all_empty = all(len(buckets[k]) == 0 for k in CATEGORY_KEYS)
    warning = "NO_FIT_ANY_CATEGORY" if all_empty else None

    # ---- shape response (FR-2 / FR-3 / FR-4).
    recommendations = RecommendationsBlock(
        desk=[_to_item(s) for s in buckets["desk"]],
        bed=[_to_item(s) for s in buckets["bed"]],
        chair=[_to_item(s) for s in buckets["chair"]],
        lighting=[_to_item(s) for s in buckets["lighting"]],
    )

    processing_ms = int(round((time.perf_counter() - t0) * 1000.0))
    if processing_ms <= 0:
        processing_ms = 1

    response = RecommendResponse(
        roomId=body.roomId,
        status="OK",
        resolvedStyle=resolved,  # type: ignore[arg-type]
        generatedAt=_now_iso8601(),
        recommendations=recommendations,
        warning=warning,
        processingMs=processing_ms,
    )
    log.info(
        "recommend ok roomId=%s resolvedStyle=%s desk=%d bed=%d chair=%d lighting=%d warning=%s processingMs=%d",
        body.roomId,
        resolved,
        len(response.recommendations.desk),
        len(response.recommendations.bed),
        len(response.recommendations.chair),
        len(response.recommendations.lighting),
        warning,
        processing_ms,
    )
    return response


def _to_item(s) -> RecommendationItemModel:
    """Convert a scorer :class:`~app.analyzers.recommender.ScoredItem` to a
    pydantic response model."""
    return RecommendationItemModel(
        furnitureId=s.furnitureId,
        name=s.name,
        type=s.type,  # type: ignore[arg-type]
        price=s.price,
        imageUrl=s.imageUrl,
        fitScore=s.fitScore,
        scoreBreakdown=ScoreBreakdownModel(
            sizeFit=s.scoreBreakdown.sizeFit,
            styleMatch=s.scoreBreakdown.styleMatch,
            colorHarmony=s.scoreBreakdown.colorHarmony,
            objectConflict=s.scoreBreakdown.objectConflict,
        ),
        rationale=s.rationale,
    )


def _now_iso8601() -> str:
    """RFC3339 / ISO-8601 UTC with seconds precision + trailing Z."""
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
