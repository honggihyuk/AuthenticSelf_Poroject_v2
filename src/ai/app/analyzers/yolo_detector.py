"""``YOLODetector`` — Ultralytics YOLOv8 wrapper for furniture detection.

Loads the pretrained ``yolov8n.pt`` (COCO) weights lazily once per process.
COCO has direct labels for typical furniture: chair, couch, bed, dining table,
toilet, tv, laptop, refrigerator, oven, microwave, sink, potted plant, etc.

Output is a list of :class:`Detection` (label, bbox, confidence) — coordinates
are absolute pixel positions in the original image (xyxy format).
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

import cv2
import numpy as np

from app.errors import ImageNotFoundError, ImageReadError

log = logging.getLogger("yolo-detector")


@dataclass(frozen=True)
class Detection:
    label: str
    bbox: tuple                # (x1, y1, x2, y2) in pixels
    confidence: float


@dataclass(frozen=True)
class DetectionResult:
    detections: List[Detection]
    image_width: int
    image_height: int


_MODEL = None
_LOCK = threading.Lock()
_MODEL_NAME = "yolov8n.pt"
_CONF_THRESHOLD = 0.25


def _get_model():
    """Lazy-load the YOLO model exactly once per process."""
    global _MODEL
    if _MODEL is not None:
        return _MODEL
    with _LOCK:
        if _MODEL is None:
            from ultralytics import YOLO
            log.info("loading YOLOv8 weights model=%s", _MODEL_NAME)
            _MODEL = YOLO(_MODEL_NAME)
            log.info("YOLOv8 ready")
    return _MODEL


class YOLODetector:
    """Run YOLOv8n inference and return absolute-pixel bboxes."""

    def detect(self, image_path: Path) -> DetectionResult:
        if not image_path.exists() or not image_path.is_file():
            raise ImageNotFoundError(f"Resolved path does not exist: {image_path.name}")

        buf = np.fromfile(str(image_path), dtype=np.uint8)
        if buf.size == 0:
            raise ImageReadError(f"File is empty: {image_path.name}")
        img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
        if img is None:
            raise ImageReadError(f"cv2.imdecode failed: {image_path.name}")

        h, w = img.shape[:2]
        model = _get_model()
        results = model.predict(
            source=img,
            conf=_CONF_THRESHOLD,
            verbose=False,
            device="cpu",
        )

        detections: List[Detection] = []
        for r in results:
            boxes = getattr(r, "boxes", None)
            if boxes is None:
                continue
            names = r.names
            xyxy = boxes.xyxy.cpu().numpy()
            confs = boxes.conf.cpu().numpy()
            classes = boxes.cls.cpu().numpy().astype(int)
            for i in range(len(boxes)):
                x1, y1, x2, y2 = xyxy[i].tolist()
                detections.append(
                    Detection(
                        label=str(names[int(classes[i])]),
                        bbox=(round(x1, 1), round(y1, 1), round(x2, 1), round(y2, 1)),
                        confidence=round(float(confs[i]), 3),
                    )
                )

        return DetectionResult(
            detections=detections,
            image_width=int(w),
            image_height=int(h),
        )


_SINGLETON: Optional[YOLODetector] = None


def default_yolo_detector() -> YOLODetector:
    global _SINGLETON
    if _SINGLETON is None:
        _SINGLETON = YOLODetector()
    return _SINGLETON
