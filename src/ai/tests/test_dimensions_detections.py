"""Unit tests for the UC-ML-PERSIST detection-wiring in ``DimensionsEstimator``.

These verify FR-1/FR-2/AC-4/AC-5 at the analyzer level WITHOUT loading the
real YOLO or MiDaS weights. Fakes count ``detect()`` invocations so the
"zero extra inference" hard constraint (AC-4) is machine-checkable.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

from app.analyzers.dimensions import DimensionsEstimator
from app.analyzers.yolo_detector import Detection, DetectionResult
from app.analyzers.depth import DepthResult


class CountingYolo:
    """Fake YOLO detector that records how many times ``detect`` was called."""

    def __init__(self, detections, w=1280, h=960):
        self._detections = detections
        self._w = w
        self._h = h
        self.calls = 0

    def detect(self, image_path: Path) -> DetectionResult:
        self.calls += 1
        return DetectionResult(
            detections=self._detections,
            image_width=self._w,
            image_height=self._h,
        )


class FakeDepth:
    """Fake MiDaS that returns a flat inverse-depth map of the given size."""

    def __init__(self, w=1280, h=960):
        self._w = w
        self._h = h

    def estimate(self, image_path: Path) -> DepthResult:
        depth = np.full((self._h, self._w), 0.5, dtype=np.float32)
        return DepthResult(depth_map=depth, image_width=self._w, image_height=self._h)


@pytest.fixture
def real_image(tmp_path) -> Path:
    """A real, decodable image file (DimensionsEstimator existence-checks it)."""
    import cv2

    p = tmp_path / "room.jpg"
    arr = np.full((960, 1280, 3), 200, dtype=np.uint8)
    cv2.imwrite(str(p), arr)
    return p


def test_ac4_detect_called_exactly_once(real_image):
    """AC-4 — detect() is invoked exactly once and detections are populated
    from that single result; no second inference is introduced."""
    yolo = CountingYolo(
        detections=[Detection(label="chair", bbox=(120.0, 340.5, 410.2, 880.0), confidence=0.91)]
    )
    est = DimensionsEstimator(yolo=yolo, depth=FakeDepth())

    result = est.analyze(real_image)

    assert yolo.calls == 1
    assert len(result.detections) == 1
    d = result.detections[0]
    assert d.label == "chair"
    assert tuple(d.bbox) == (120.0, 340.5, 410.2, 880.0)
    assert d.confidence == 0.91
    assert result.imageWidth == 1280
    assert result.imageHeight == 960


def test_ac5_empty_detections_still_carries_dims(real_image):
    """AC-5 — when YOLO finds nothing, ``detections`` is [] (not None) and the
    image dimensions are still present on the result."""
    yolo = CountingYolo(detections=[])
    est = DimensionsEstimator(yolo=yolo, depth=FakeDepth())

    result = est.analyze(real_image)

    assert yolo.calls == 1
    assert result.detections == []
    assert result.imageWidth == 1280
    assert result.imageHeight == 960


def test_detection_shape_passthrough(real_image):
    """FR-2 — detection label/bbox/confidence are forwarded verbatim from the
    existing YOLO result (no re-shaping of coordinates)."""
    dets = [
        Detection(label="chair", bbox=(1.0, 2.0, 3.0, 4.0), confidence=0.5),
        Detection(label="bed", bbox=(600.0, 200.0, 1240.0, 900.0), confidence=0.74),
    ]
    yolo = CountingYolo(detections=dets)
    est = DimensionsEstimator(yolo=yolo, depth=FakeDepth())

    result = est.analyze(real_image)

    assert [d.label for d in result.detections] == ["chair", "bed"]
    assert tuple(result.detections[1].bbox) == (600.0, 200.0, 1240.0, 900.0)
