"""FastAPI entry-point for the AI service (FR-1..FR-3 of UC-01-space-analysis + Task-4).

Run locally from ``src/ai/``::

    uvicorn app.main:app --port 8001 --reload

The service exposes the following routes:

* ``GET  /health``         — liveness (UC-01-space-analysis FR-2, AC-3)
* ``POST /analyze/space``      — space analysis (UC-01-space-analysis FR-3)
* ``POST /analyze/style``      — style classification (UC-01-style-selection FR-1)
* ``POST /recommend/furniture`` — furniture recommendations (UC-01-recommendation FR-1)
"""

from __future__ import annotations

import asyncio
import logging
import os
import time
from pathlib import Path
from typing import Optional
from urllib.parse import unquote, urlparse

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app import __version__
from app.analyzers import AnalyzerBundle, default_analyzers
from app.errors import (
    AnalysisError,
    AnalyzerError,
    IMAGE_NOT_FOUND,
    INTERNAL_ERROR,
    INVALID_REQUEST,
    ImageNotFoundError,
    ImageReadError,
)
from app.routes.objects import router as objects_router
from app.routes.recommend import router as recommend_router
from app.routes.style import router as style_router
from app.schemas import (
    Dimensions,
    ErrorResponse,
    SpaceAnalysisResponse,
    SpaceAnalyzeRequest,
)


# ---------------------------------------------------------------------------
# Logger (FR-18) — stdout only; no image bytes, no full absolute paths at INFO.
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=os.environ.get("AI_LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s - %(message)s",
)
log = logging.getLogger("space-analysis")


# ---------------------------------------------------------------------------
# Photo-root allow-list (NFR security, AC-9).
# If unset, escape-to-arbitrary-path is disabled by falling back to the repo
# var/ directory that UC-01-photo-upload writes into.
# ---------------------------------------------------------------------------
_DEFAULT_PHOTO_ROOT = (
    Path(__file__).resolve().parents[3] / "var" / "object-storage"
)


def _photo_root() -> Path:
    """Resolve the allow-list root at call-time so env changes are honoured in tests."""
    raw = os.environ.get("AI_PHOTO_ROOT")
    root = Path(raw) if raw else _DEFAULT_PHOTO_ROOT
    try:
        return root.resolve()
    except OSError:
        return root


def _file_url_to_path(photo_url: str) -> Optional[Path]:
    """Parse a ``file://`` URL into an absolute :class:`Path`.

    Returns ``None`` if the URL is not a ``file://`` scheme. Handles the
    Windows quirk of ``file:///C:/...`` where ``netloc`` is empty and the
    drive letter lives at the start of ``path``.
    """
    try:
        parsed = urlparse(photo_url)
    except ValueError:
        return None
    if parsed.scheme != "file":
        return None

    raw_path = unquote(parsed.path or "")
    # On Windows `file:///C:/...` parses to path='/C:/...'; strip the leading
    # slash so Path doesn't anchor to the filesystem root.
    if os.name == "nt" and len(raw_path) >= 3 and raw_path[0] == "/" and raw_path[2] == ":":
        raw_path = raw_path[1:]

    if not raw_path:
        return None
    return Path(raw_path)


# ---------------------------------------------------------------------------
# App factory
# ---------------------------------------------------------------------------

app = FastAPI(
    title="AuthenticSelf AI — Space Analysis",
    version=__version__,
    description=(
        "Vision service for the AuthenticSelf platform. "
        "Routes: `/health`, `/analyze/space`, `/analyze/style`, `/recommend/furniture`."
    ),
)

# Task-4 router — `POST /analyze/style`. Registered here so `/openapi.json`
# reports both routes (AC-1). See `app/routes/style.py` for the handler.
app.include_router(style_router)

# Task-5 router — `POST /recommend/furniture` (UC-01-recommendation FR-1, AC-1).
app.include_router(recommend_router)

# YOLO object-detection router — `POST /analyze/objects`.
app.include_router(objects_router)


# ---------------------------------------------------------------------------
# Exception handlers (FR-7).
# One handler per error family, all emitting the structured envelope.
# ---------------------------------------------------------------------------


@app.exception_handler(RequestValidationError)
async def handle_validation(request: Request, exc: RequestValidationError) -> JSONResponse:
    # Try to surface roomId if the client at least sent a JSON body with it.
    body = None
    try:
        body = await request.json()
    except Exception:
        body = None
    room_id = None
    if isinstance(body, dict) and isinstance(body.get("roomId"), str):
        room_id = body["roomId"]
    log.warning("validation failed errorCode=%s errors=%s", INVALID_REQUEST, exc.errors())
    return JSONResponse(
        status_code=422,
        content=ErrorResponse(
            errorCode=INVALID_REQUEST,
            message="Request body failed validation.",
            roomId=room_id,
        ).model_dump(),
    )


@app.exception_handler(AnalyzerError)
async def handle_analyzer_error(request: Request, exc: AnalyzerError) -> JSONResponse:
    # roomId was parsed before the analyzers ran — stash it on request.state.
    room_id = getattr(request.state, "room_id", None)
    log.warning(
        "analyzer error errorCode=%s roomId=%s msg=%s",
        exc.code, room_id, exc.message,
    )
    return JSONResponse(
        status_code=422,
        content=ErrorResponse(
            errorCode=exc.code,
            message=exc.message,
            roomId=room_id,
        ).model_dump(),
    )


@app.exception_handler(Exception)
async def handle_uncaught(request: Request, exc: Exception) -> JSONResponse:
    room_id = getattr(request.state, "room_id", None)
    log.error("internal error roomId=%s exc=%r", room_id, exc, exc_info=exc)
    return JSONResponse(
        status_code=500,
        content=ErrorResponse(
            errorCode=INTERNAL_ERROR,
            message="Unexpected internal error.",
            roomId=room_id,
        ).model_dump(),
    )


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@app.get("/health")
async def health() -> dict:
    """Liveness probe (FR-2 / AC-3)."""
    return {"status": "ok", "service": "space-analysis", "version": __version__}


@app.post(
    "/analyze/space",
    response_model=SpaceAnalysisResponse,
    responses={
        422: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
)
async def analyze_space(
    request: Request,
    body: SpaceAnalyzeRequest,
    analyzers: AnalyzerBundle = Depends(default_analyzers),
) -> SpaceAnalysisResponse:
    """Analyze a room photo (FR-3, FR-6, FR-8, FR-9).

    Handler is ``async def`` and every analyzer runs under
    ``asyncio.to_thread`` so the event loop stays responsive under the
    parallel calls Task 4 will make (AC-10).
    """
    t0 = time.perf_counter()
    request.state.room_id = body.roomId
    log.info("analyze/space start roomId=%s", body.roomId)

    # ---- resolve + path-escape hardening (AC-9) ---------------------------
    path = _file_url_to_path(body.photoUrl)
    if path is None:
        raise ImageNotFoundError("photoUrl did not resolve to a file:// path.")

    try:
        resolved = path.resolve(strict=False)
    except OSError as e:
        raise ImageNotFoundError(f"Path could not be resolved: {path.name}") from e

    root = _photo_root()
    try:
        resolved.relative_to(root)
    except ValueError:
        # Resolves outside the allow-list root — treat as not-found so the
        # caller cannot distinguish "escape" from "absent" (FR-7 / AC-9).
        log.warning(
            "path escape rejected roomId=%s root=%s name=%s",
            body.roomId, root, path.name,
        )
        raise ImageNotFoundError(
            f"Resolved path outside allowed root: {path.name}"
        )

    if not resolved.exists() or not resolved.is_file():
        raise ImageNotFoundError(f"Resolved path does not exist: {path.name}")

    # ---- run analyzers off the event loop (FR-8, AC-10) ------------------
    try:
        dim_result, color_result = await asyncio.gather(
            asyncio.to_thread(analyzers.dimensions.analyze, resolved),
            asyncio.to_thread(analyzers.color.analyze, resolved),
        )
    except AnalyzerError:
        # Let the dedicated handler map the structured error.
        raise
    except Exception as e:  # defensive — unknown analyzer bug
        log.error("analyzer unexpected failure roomId=%s exc=%r", body.roomId, e)
        raise AnalysisError("Analyzer pipeline failed unexpectedly.") from e

    # ---- compose response (FR-9) ----------------------------------------
    dimensions = Dimensions(
        widthM=dim_result.widthM,
        lengthM=dim_result.lengthM,
        heightM=dim_result.heightM,
        areaM2=round(dim_result.widthM * dim_result.lengthM, 2),
    )
    aggregate_confidence = round(
        (dim_result.confidence + color_result.confidence) / 2.0, 2
    )

    processing_ms = int(round((time.perf_counter() - t0) * 1000.0))
    # Guarantee AC-4's `processingMs > 0` even on a very fast synthetic image.
    if processing_ms <= 0:
        processing_ms = 1

    response = SpaceAnalysisResponse(
        roomId=body.roomId,
        status="OK",
        dimensions=dimensions,
        mainColor=color_result.mainColor,
        confidence=aggregate_confidence,
        processingMs=processing_ms,
    )
    log.info(
        "analyze/space ok roomId=%s processingMs=%d dimensions=%sx%sx%sm mainColor=%s confidence=%.2f",
        body.roomId,
        processing_ms,
        dimensions.widthM,
        dimensions.lengthM,
        dimensions.heightM,
        color_result.mainColor,
        aggregate_confidence,
    )
    return response


# Ensure raised ImageNotFoundError / ImageReadError keep their subclass-specific
# error code instead of being caught by the base AnalyzerError handler only.
# (FastAPI's exception-handler lookup walks the MRO, so the single handler
# above suffices — listed here for documentation.)
_ = (ImageNotFoundError, ImageReadError, AnalysisError)
