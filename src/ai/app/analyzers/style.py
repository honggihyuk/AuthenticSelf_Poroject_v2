"""``StyleClassifier`` — deterministic placeholder (FR-3, FR-4, AC-8).

This class deterministically classifies a room photo into one of five
interior-design styles, based on the SHA-256 digest of the image bytes.
It is **intentionally not a real ML model**; it exists so that UC-01
(style-selection) can wire a fully-typed response end-to-end without
pulling heavy ML frameworks into the runtime-deps set.

TODO(ml): replace with a real style classifier (e.g. ResNet/CLIP-based
transformer) in a future task. The hash-based stub below preserves
deterministic I/O — identical bytes yield identical scores — so the
Spring + RN side contracts can be locked in before the real model lands.

Hash-to-score mapping (documented per FR-4):

1. Read image bytes and compute ``sha256(bytes)`` (32-byte digest).
2. Take the first 20 hex characters of that digest.
3. Split them into five 4-char chunks; each chunk parses as a 16-bit int
   in [0, 65535].
4. Divide by 65535.0 to produce five raw floats in [0, 1].
5. Normalise them to sum exactly to 1.0 (re-normalise after rounding so
   the tolerance in FR-2's score-sum check stays satisfied).
6. ``argmax`` of the normalised vector is the returned style.

``CURRENT`` is a user-only choice (PreferredStyle) and is never emitted
here — the enum below has exactly the five AI-output labels (AC-4).
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Tuple

from app.errors import ImageNotFoundError, ImageReadError


# Canonical AI-output labels — exactly the five values the Spring `Style`
# enum expects. `CURRENT` is deliberately absent (FR-8 / AC-10 / AC-4).
STYLE_LABELS: Tuple[str, ...] = (
    "MODERN",
    "SIMPLE",
    "CLASSIC",
    "SCANDINAVIAN",
    "INDUSTRIAL",
)


@dataclass(frozen=True)
class StyleAnalysisResult:
    """Structured output returned by :class:`StyleClassifier`.

    ``scores`` keys are exactly :data:`STYLE_LABELS`. ``style`` is the
    argmax; ``confidence`` equals ``scores[style]`` rounded to 2dp.
    """

    style: str
    confidence: float
    scores: Dict[str, float]


class StyleClassifier:
    """PLACEHOLDER style analyzer — hash-based, deterministic (FR-3, FR-4).

    TODO(ml): swap for a real classifier (ResNet / CLIP / transformer) in
    a future task. No mutable state is carried across calls, so the class
    is trivially thread-safe and can be invoked concurrently from
    ``asyncio.to_thread``.
    """

    # Exposed for Task 4 test parity — downstream callers grab the list
    # via ``StyleClassifier.LABELS`` rather than reaching into module globals.
    LABELS: Tuple[str, ...] = STYLE_LABELS

    def analyze(self, image_path: Path) -> StyleAnalysisResult:
        """Return a deterministic style classification for the image at ``image_path``.

        Raises:
            ImageNotFoundError: if the path does not exist / is not a file.
            ImageReadError: if the file is empty or unreadable.
        """
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

        # ---- hash → five raw floats (FR-4) -------------------------------
        digest_hex = hashlib.sha256(raw).hexdigest()[:20]  # 20 hex chars -> 5 chunks of 4
        chunks = [digest_hex[i : i + 4] for i in range(0, 20, 4)]
        raw_vals = [int(c, 16) / 65535.0 for c in chunks]  # each in [0,1]

        # Ensure no all-zero vector (extremely unlikely given sha256, but defensive).
        total_raw = sum(raw_vals)
        if total_raw <= 0.0:
            raw_vals = [1.0] * len(STYLE_LABELS)
            total_raw = float(len(STYLE_LABELS))

        # ---- normalise, round to 2 dp, re-normalise to tolerance band ----
        normalised = [v / total_raw for v in raw_vals]
        rounded = [round(v, 2) for v in normalised]

        # After 2-dp rounding the sum may drift; nudge the argmax so the
        # sum is in [0.99, 1.01] (FR-2).
        s = round(sum(rounded), 2)
        if s != 1.00:
            delta = round(1.00 - s, 2)
            # Apply the correction to the currently-largest value so the
            # argmax is preserved.
            idx_max = rounded.index(max(rounded))
            rounded[idx_max] = round(rounded[idx_max] + delta, 2)

        scores: Dict[str, float] = {
            label: score for label, score in zip(STYLE_LABELS, rounded)
        }

        # ---- pick the argmax ---------------------------------------------
        style = max(scores, key=lambda k: scores[k])
        confidence = round(scores[style], 2)

        return StyleAnalysisResult(
            style=style,
            confidence=confidence,
            scores=scores,
        )
