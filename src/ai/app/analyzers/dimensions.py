"""``DimensionsEstimator`` — PLACEHOLDER implementation (FR-5).

TODO(ml): replace with real LayoutNet / YOLO-based inference in a future
task. This placeholder does NOT import any ML runtime framework and does
NOT load any model weights (AC-2).

Deterministic mapping
---------------------
Hash the image bytes (SHA-256) and use two byte-slices of the digest to
pick values inside bounded ranges:

* ``widthM``  = 2.5 + (digest[0:4]  as uint32) / 2**32 * 3.5   → [2.5, 6.0)
* ``lengthM`` = 2.5 + (digest[4:8]  as uint32) / 2**32 * 3.5   → [2.5, 6.0)
* ``heightM`` = 2.4  (fixed — typical residential ceiling)
* ``confidence`` = 0.60 + (digest[8:12] as uint32) / 2**32 * 0.35 → [0.60, 0.95)

Values are rounded to 2 dp. Identical input bytes always produce identical
output — required for AC-5 (determinism).
"""

from __future__ import annotations

import hashlib
from pathlib import Path

from app.errors import ImageNotFoundError, ImageReadError
from .base import SpaceAnalysisResult


_FIXED_HEIGHT_M = 2.4
_UINT32_MAX = float(1 << 32)


class DimensionsEstimator:
    """PLACEHOLDER dimensions analyzer — deterministic hash-driven stub (FR-5).

    TODO(ml): swap for real LayoutNet/YOLO inference in a future task.
    """

    def analyze(self, image_path: Path) -> SpaceAnalysisResult:  # noqa: D401
        """Return deterministic pseudo-dimensions from the SHA-256 of the file."""
        if not image_path.exists() or not image_path.is_file():
            raise ImageNotFoundError(
                f"Resolved path does not exist: {image_path.name}"
            )

        try:
            raw = image_path.read_bytes()
        except OSError as e:
            raise ImageReadError(
                f"Could not read file bytes: {image_path.name}"
            ) from e

        if not raw:
            raise ImageReadError(f"File is empty: {image_path.name}")

        digest = hashlib.sha256(raw).digest()

        def _u32(offset: int) -> int:
            return int.from_bytes(digest[offset : offset + 4], "big", signed=False)

        width_m = 2.5 + (_u32(0) / _UINT32_MAX) * 3.5
        length_m = 2.5 + (_u32(4) / _UINT32_MAX) * 3.5
        confidence = 0.60 + (_u32(8) / _UINT32_MAX) * 0.35

        # Round to one decimal place for width/length for nicer UX and to
        # ensure the AC-16 regex matches (^\d+(\.\d+)?x...).
        width_m = round(width_m, 1)
        length_m = round(length_m, 1)
        confidence = round(confidence, 2)

        return SpaceAnalysisResult(
            widthM=width_m,
            lengthM=length_m,
            heightM=_FIXED_HEIGHT_M,
            confidence=confidence,
        )
