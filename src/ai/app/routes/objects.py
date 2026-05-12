"""``POST /analyze/objects`` route — YOLO furniture detection.

Returns absolute-pixel bounding boxes for objects the YOLOv8n COCO model
detects in the room photo. Same file:// allow-list + asyncio.to_thread
plumbing as ``/analyze/space`` and ``/analyze/style``.
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

from app.analyzers.yolo_detector import YOLODetector, default_yolo_detector
from app.errors import AnalysisError, AnalyzerError, ImageNotFoundError, ImageReadError
from app.schemas import (
    DetectedObject,
    ErrorResponse,
    ObjectsAnalysisResponse,
    ObjectsAnalyzeRequest,
)


log = logging.getLogger("objects-analysis")


_DEFAULT_PHOTO_ROOT = (
    Path(__file__).resolve().parents[3] / "var" / "object-storage"
)


def _photo_root() -> Path:
    raw = os.environ.get("AI_PHOTO_ROOT")
    root = Path(raw) if raw else _DEFAULT_PHOTO_ROOT
    try:
        return root.resolve()
    except OSError:
        return root


def _file_url_to_path(photo_url: str) -> Optional[Path]:
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
    "/analyze/objects",
    response_model=ObjectsAnalysisResponse,
    responses={
        422: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
)
async def analyze_objects(
    request: Request,
    body: ObjectsAnalyzeRequest,
    detector: YOLODetector = Depends(default_yolo_detector),
) -> ObjectsAnalysisResponse:
    t0 = time.perf_counter()
    request.state.room_id = body.roomId
    log.info("analyze/objects start roomId=%s", body.roomId)

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
        raise ImageNotFoundError(f"Resolved path outside allowed root: {path.name}")

    if not resolved.exists() or not resolved.is_file():
        raise ImageNotFoundError(f"Resolved path does not exist: {path.name}")

    try:
        result = await asyncio.to_thread(detector.detect, resolved)
    except AnalyzerError:
        raise
    except Exception as e:
        log.error("yolo detector unexpected failure roomId=%s exc=%r", body.roomId, e)
        raise AnalysisError("YOLO detector failed unexpectedly.") from e

    processing_ms = max(1, int(round((time.perf_counter() - t0) * 1000.0)))

    response = ObjectsAnalysisResponse(
        roomId=body.roomId,
        status="OK",
        imageWidth=result.image_width,
        imageHeight=result.image_height,
        objects=[
            DetectedObject(label=d.label, bbox=d.bbox, confidence=d.confidence)
            for d in result.detections
        ],
        processingMs=processing_ms,
    )

    log.info(
        "analyze/objects ok roomId=%s processingMs=%d objects=%d",
        body.roomId, processing_ms, len(result.detections),
    )
    return response


_ = (ImageNotFoundError, ImageReadError, AnalysisError)
