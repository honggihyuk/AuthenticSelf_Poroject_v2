"""``DimensionsEstimator`` — YOLO + MiDaS based room dimension estimate.

Single-image absolute metric depth is ill-posed. We make it tractable by
combining two sources:

1. **YOLOv8 (COCO)** finds a reference object of a *typical* known real-world
   width (couch ≈ 2.0 m, bed ≈ 1.5 m, chair ≈ 0.5 m, …). From its pixel
   bounding-box width we derive a meters-per-pixel scale **at that object's
   depth plane**.

2. **MiDaS** gives a relative inverse-depth map. The ratio between the
   reference object's depth and the room's far depth approximates how much
   farther the back wall is than the reference, so we can estimate length.

Outputs:
  - ``widthM``  ≈ pixel image width * meters-per-pixel-at-reference
  - ``lengthM`` ≈ widthM * (far_depth / reference_depth)
  - ``heightM`` = 2.4 (standard residential ceiling assumption; one image cannot
    recover this without a vertical reference)
  - ``confidence`` ∈ {0.55, 0.25} depending on whether a reference object was
    found.

The result is a *rough* estimate, not a survey. Documented as a heuristic.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional, Tuple

import numpy as np

from app.errors import ImageNotFoundError, ImageReadError
from .base import SpaceAnalysisResult
from .depth import DepthEstimator, default_depth_estimator
from .yolo_detector import Detection, YOLODetector, default_yolo_detector

log = logging.getLogger("dimensions-estimator")

_FIXED_HEIGHT_M = 2.4

TYPICAL_WIDTHS_M = {
    "couch": 2.0,
    "bed": 1.5,
    "dining table": 1.5,
    "chair": 0.5,
    "tv": 1.0,
    "refrigerator": 0.7,
    "person": 0.45,
    "potted plant": 0.3,
    "laptop": 0.35,
    "toilet": 0.4,
    "oven": 0.6,
    "microwave": 0.5,
    "sink": 0.6,
    "book": 0.15,
}


def _pick_reference(detections) -> Optional[Detection]:
    """Pick the highest-confidence detection whose label has a typical width."""
    candidates = [d for d in detections if d.label in TYPICAL_WIDTHS_M]
    if not candidates:
        return None
    return max(candidates, key=lambda d: d.confidence)


def _bbox_depth_median(depth_map: np.ndarray, bbox: Tuple[float, float, float, float]) -> float:
    """Return the median inverse-depth value inside the bbox (clamped to image)."""
    h, w = depth_map.shape[:2]
    x1, y1, x2, y2 = bbox
    x1 = max(0, int(round(x1)))
    y1 = max(0, int(round(y1)))
    x2 = min(w, int(round(x2)))
    y2 = min(h, int(round(y2)))
    if x2 <= x1 or y2 <= y1:
        return float(np.median(depth_map))
    return float(np.median(depth_map[y1:y2, x1:x2]))


class DimensionsEstimator:
    """YOLO + MiDaS based dimension estimator (heuristic, single-image)."""

    def __init__(
        self,
        yolo: Optional[YOLODetector] = None,
        depth: Optional[DepthEstimator] = None,
    ) -> None:
        self._yolo = yolo or default_yolo_detector()
        self._depth = depth or default_depth_estimator()

    def analyze(self, image_path: Path) -> SpaceAnalysisResult:
        if not image_path.exists() or not image_path.is_file():
            raise ImageNotFoundError(f"Resolved path does not exist: {image_path.name}")

        det = self._yolo.detect(image_path)
        dep = self._depth.estimate(image_path)
        img_w, img_h = dep.image_width, dep.image_height
        depth_map = dep.depth_map

        ref = _pick_reference(det.detections)

        if ref is None:
            width_m = 3.5
            length_m = 3.5
            confidence = 0.25
            log.info("no reference object found; using fallback dimensions")
        else:
            real_w = TYPICAL_WIDTHS_M[ref.label]
            px_w = max(1.0, ref.bbox[2] - ref.bbox[0])
            mpp_at_ref = real_w / px_w

            width_m = img_w * mpp_at_ref
            ref_depth_inv = max(_bbox_depth_median(depth_map, ref.bbox), 1e-3)
            far_depth_inv = max(float(depth_map.min()), 1e-3)
            depth_ratio = ref_depth_inv / far_depth_inv

            length_m = width_m * float(np.clip(depth_ratio, 0.6, 2.0))

            width_m = float(np.clip(width_m, 1.5, 12.0))
            length_m = float(np.clip(length_m, 1.5, 12.0))
            confidence = round(min(0.85, 0.45 + 0.4 * ref.confidence), 2)

            log.info(
                "ref=%s conf=%.2f real_w=%.2fm px_w=%.0f mpp=%.4f -> width=%.2fm length=%.2fm",
                ref.label, ref.confidence, real_w, px_w, mpp_at_ref, width_m, length_m,
            )

        return SpaceAnalysisResult(
            widthM=round(width_m, 2),
            lengthM=round(length_m, 2),
            heightM=_FIXED_HEIGHT_M,
            confidence=round(confidence, 2),
        )
