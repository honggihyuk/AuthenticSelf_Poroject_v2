"""``StyleClassifier`` — HSV-histogram heuristic (simplified style rule).

Single-image style detection without a trained classifier. We compute three
features from the image and apply five linear rules that bias each style
score. The five output labels match the Spring ``Style`` enum exactly
(``CURRENT`` is user-only and is never emitted here).

Features:
  - ``mean_v``    — average HSV brightness (0..1)
  - ``mean_s``    — average HSV saturation (0..1)
  - ``warm_ratio`` — fraction of pixels with warm hue (red/orange/yellow) AND
                    non-trivial saturation

Rules (additive, all start from a small base):
  - SCANDINAVIAN: bright + low saturation (whites/pale-grey rooms)
  - INDUSTRIAL:   dark + low saturation (concrete/black-metal looks)
  - CLASSIC:      warm-toned pixels (wood/gold/brown)
  - MODERN:       high saturation contrast on a mid-bright base
  - SIMPLE:       neutral mid V, low saturation (the default "anything else")

Outputs sum to ~1.0 (FR-2 tolerance). ``argmax`` is the predicted style.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Tuple

import cv2
import numpy as np

from app.errors import ImageNotFoundError, ImageReadError

log = logging.getLogger("style-classifier")


STYLE_LABELS: Tuple[str, ...] = (
    "MODERN",
    "SIMPLE",
    "CLASSIC",
    "SCANDINAVIAN",
    "INDUSTRIAL",
)


@dataclass(frozen=True)
class StyleAnalysisResult:
    style: str
    confidence: float
    scores: Dict[str, float]


def _features(img_bgr: np.ndarray) -> Tuple[float, float, float]:
    hsv = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2HSV)
    h = hsv[:, :, 0].astype(np.float32)
    s = hsv[:, :, 1].astype(np.float32) / 255.0
    v = hsv[:, :, 2].astype(np.float32) / 255.0

    mean_s = float(s.mean())
    mean_v = float(v.mean())

    warm_mask = ((h < 25) | (h > 160)) & (s > 0.18)
    warm_ratio = float(warm_mask.mean())

    return mean_v, mean_s, warm_ratio


def _normalise(scores: Dict[str, float]) -> Dict[str, float]:
    total = sum(scores.values())
    if total <= 0.0:
        return {k: round(1.0 / len(scores), 2) for k in scores}
    normalised = {k: v / total for k, v in scores.items()}
    rounded = {k: round(v, 2) for k, v in normalised.items()}
    s = round(sum(rounded.values()), 2)
    if s != 1.00:
        delta = round(1.00 - s, 2)
        argmax = max(rounded, key=rounded.get)
        rounded[argmax] = round(rounded[argmax] + delta, 2)
    return rounded


class StyleClassifier:
    """HSV-histogram heuristic style classifier (FR-3, FR-4)."""

    LABELS: Tuple[str, ...] = STYLE_LABELS

    def analyze(self, image_path: Path) -> StyleAnalysisResult:
        if not image_path.exists() or not image_path.is_file():
            raise ImageNotFoundError(f"Resolved path does not exist: {image_path.name}")

        buf = np.fromfile(str(image_path), dtype=np.uint8)
        if buf.size == 0:
            raise ImageReadError(f"File is empty: {image_path.name}")
        img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
        if img is None:
            raise ImageReadError(f"cv2.imdecode failed: {image_path.name}")

        h, w = img.shape[:2]
        long_edge = max(h, w)
        if long_edge > 320:
            scale = 320.0 / long_edge
            img = cv2.resize(
                img, (max(1, int(w * scale)), max(1, int(h * scale))),
                interpolation=cv2.INTER_AREA,
            )

        mean_v, mean_s, warm_ratio = _features(img)

        scores: Dict[str, float] = {label: 0.10 for label in STYLE_LABELS}

        if mean_v > 0.72 and mean_s < 0.22:
            scores["SCANDINAVIAN"] += 0.65
        if mean_v < 0.42 and mean_s < 0.30:
            scores["INDUSTRIAL"] += 0.60
        if warm_ratio > 0.30 and mean_s > 0.22:
            scores["CLASSIC"] += 0.55
        if mean_s > 0.38 and 0.45 <= mean_v <= 0.80:
            scores["MODERN"] += 0.50
        if mean_s < 0.30 and 0.42 <= mean_v <= 0.72 and warm_ratio < 0.30:
            scores["SIMPLE"] += 0.45

        rounded = _normalise(scores)
        style = max(rounded, key=rounded.get)
        confidence = round(rounded[style], 2)

        log.info(
            "style=%s conf=%.2f v=%.2f s=%.2f warm=%.2f",
            style, confidence, mean_v, mean_s, warm_ratio,
        )

        return StyleAnalysisResult(style=style, confidence=confidence, scores=rounded)
