"""``POST /analyze/style`` route (FR-1, FR-2, FR-3, FR-6, FR-7).

Mirrors ``app.main.analyze_space`` for layout and error handling: the
handler is ``async def``, the :class:`StyleClassifier` is executed on
a thread via :func:`asyncio.to_thread`, and the path is checked against
the ``AI_PHOTO_ROOT`` allow-list before any file I/O runs.

The structured error envelope is produced by the module-level exception
handlers in :mod:`app.main` — this router only raises the corresponding
:class:`~app.errors.AnalyzerError` subclasses.
"""

from __future__ import annotations

import asyncio
import logging
import os
import time
from pathlib import Path
from typing import Optional
from urllib.parse import unquote, urlparse

from fastapi import APIRouter, Depends, Request

from app.analyzers import StyleClassifier, default_style_classifier
from app.errors import AnalysisError, AnalyzerError, ImageNotFoundError, ImageReadError
from app.schemas import ErrorResponse, StyleAnalysisResponse, StyleAnalyzeRequest


log = logging.getLogger("style-analysis")


# The photo-root / file:// URL helpers are intentionally duplicated from
# ``app.main`` rather than imported. ``app.main`` imports *this* module
# (to register the router) so importing the helpers back from main.py
# would create a circular import at application-startup time. The
# handful of lines involved are trivial and stay in lock-step via a
# shared copy-paste policy documented in each module's top-level comment.

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
    """Parse a ``file://`` URL into an absolute :class:`Path`; ``None`` otherwise."""
    try:
        parsed = urlparse(photo_url)
    except ValueError:
        return None
    if parsed.scheme != "file":
        return None

    raw_path = unquote(parsed.path or "")
    if os.name == "nt" and len(raw_path) >= 3 and raw_path[0] == "/" and raw_path[2] == ":":
        raw_path = raw_path[1:]

    if not raw_path:
        return None
    return Path(raw_path)


router = APIRouter()


@router.post(
    "/analyze/style",
    response_model=StyleAnalysisResponse,
    responses={
        422: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
)
async def analyze_style(
    request: Request,
    body: StyleAnalyzeRequest,
    classifier: StyleClassifier = Depends(default_style_classifier),
) -> StyleAnalysisResponse:
    """Classify the uploaded image into one of five interior-design styles (FR-2).

    Handler is ``async def`` and the classifier call runs under
    :func:`asyncio.to_thread` (AC-6 / FR-6).
    """
    t0 = time.perf_counter()
    request.state.room_id = body.roomId
    log.info("analyze/style start roomId=%s", body.roomId)

    # ---- resolve + path-escape hardening (FR-7) -------------------------
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
        log.warning(
            "path escape rejected roomId=%s root=%s name=%s",
            body.roomId, root, path.name,
        )
        raise ImageNotFoundError(
            f"Resolved path outside allowed root: {path.name}"
        )

    if not resolved.exists() or not resolved.is_file():
        raise ImageNotFoundError(f"Resolved path does not exist: {path.name}")

    # ---- run classifier off the event loop (FR-6, AC-6) -----------------
    try:
        result = await asyncio.to_thread(classifier.analyze, resolved)
    except AnalyzerError:
        raise
    except Exception as e:  # defensive — unknown classifier bug
        log.error("style classifier unexpected failure roomId=%s exc=%r",
                  body.roomId, e)
        raise AnalysisError("Style classifier failed unexpectedly.") from e

    # ---- compose response (FR-2) ----------------------------------------
    processing_ms = int(round((time.perf_counter() - t0) * 1000.0))
    if processing_ms <= 0:
        processing_ms = 1

    response = StyleAnalysisResponse(
        roomId=body.roomId,
        status="OK",
        style=result.style,  # type: ignore[arg-type]  # Literal narrow via pydantic
        confidence=result.confidence,
        scores=dict(result.scores),  # type: ignore[arg-type]
        processingMs=processing_ms,
    )
    log.info(
        "analyze/style ok roomId=%s processingMs=%d style=%s confidence=%.2f",
        body.roomId, processing_ms, result.style, result.confidence,
    )
    return response


# Re-export for the module-level handlers in app.main (FastAPI looks them
# up via MRO, so this line is pure documentation).
_ = (ImageNotFoundError, ImageReadError, AnalysisError)
