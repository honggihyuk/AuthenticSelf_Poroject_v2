"""``ColorExtractor`` — REAL implementation (FR-6, AC-12).

Uses OpenCV k-means over the downscaled image to pick the dominant colour
cluster and returns it as ``#RRGGBB`` uppercase hex. ``confidence`` is the
fraction of pixels belonging to the dominant cluster.
"""

from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

from app.errors import ImageNotFoundError, ImageReadError
from .base import SpaceAnalysisResult


# k-means hyper-parameters (FR-6) -------------------------------------------
_K = 5
_KMEANS_CRITERIA = (
    cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER,
    10,
    1.0,
)
_KMEANS_ATTEMPTS = 3
_KMEANS_FLAGS = cv2.KMEANS_PP_CENTERS
_DOWNSCALE_LONG_EDGE = 256


class ColorExtractor:
    """REAL ColorExtractor — OpenCV k-means dominant-colour picker (FR-6, AC-12).

    NOT a placeholder. Task 4 / 5 will reuse this analyzer unchanged.
    """

    def analyze(self, image_path: Path) -> SpaceAnalysisResult:  # noqa: D401
        """Return the dominant colour as ``#RRGGBB`` + cluster dominance confidence."""
        if not image_path.exists() or not image_path.is_file():
            raise ImageNotFoundError(
                f"Resolved path does not exist: {image_path.name}"
            )

        # Read via np.fromfile + cv2.imdecode instead of cv2.imread so
        # non-ASCII paths work on Windows (OpenCV's Windows build passes
        # the path through MBCS which silently fails on Korean/Japanese
        # user profile names). AC-7 still maps a failed decode to
        # IMAGE_READ_FAILED.
        try:
            buf = np.fromfile(str(image_path), dtype=np.uint8)
        except OSError as e:
            raise ImageReadError(
                f"Could not read file bytes: {image_path.name}"
            ) from e
        if buf.size == 0:
            raise ImageReadError(
                f"File is empty: {image_path.name}"
            )
        img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
        if img is None:
            raise ImageReadError(
                f"cv2.imdecode could not decode file: {image_path.name}"
            )

        # --- downscale so kmeans is fast + deterministic on large images ---
        h, w = img.shape[:2]
        long_edge = max(h, w)
        if long_edge > _DOWNSCALE_LONG_EDGE:
            scale = _DOWNSCALE_LONG_EDGE / float(long_edge)
            new_w = max(1, int(round(w * scale)))
            new_h = max(1, int(round(h * scale)))
            img = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_AREA)

        # BGR -> RGB for honest hex output
        rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        pixels = rgb.reshape(-1, 3).astype(np.float32)

        # Clamp K to the number of unique pixels — cv2.kmeans otherwise errors
        # on tiny synthetic test images.
        k = min(_K, max(1, pixels.shape[0]))

        # cv2.kmeans signature:
        #   retval, bestLabels, centers = cv2.kmeans(
        #       data, K, bestLabels, criteria, attempts, flags)
        _compactness, labels, centers = cv2.kmeans(
            pixels,
            k,
            None,
            _KMEANS_CRITERIA,
            _KMEANS_ATTEMPTS,
            _KMEANS_FLAGS,
        )

        # Find the cluster with the largest pixel count.
        labels_flat = labels.flatten()
        counts = np.bincount(labels_flat, minlength=k)
        dominant_idx = int(np.argmax(counts))
        dominant_centroid = centers[dominant_idx]
        r = int(round(float(dominant_centroid[0])))
        g = int(round(float(dominant_centroid[1])))
        b = int(round(float(dominant_centroid[2])))
        r = max(0, min(255, r))
        g = max(0, min(255, g))
        b = max(0, min(255, b))

        hex_color = f"#{r:02X}{g:02X}{b:02X}"

        total_px = int(labels_flat.size)
        dominance = float(counts[dominant_idx]) / float(total_px) if total_px else 0.0
        confidence = round(max(0.0, min(1.0, dominance)), 2)

        return SpaceAnalysisResult(
            mainColor=hex_color,
            confidence=confidence,
        )
