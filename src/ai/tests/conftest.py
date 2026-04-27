"""Shared pytest fixtures for the AI service tests.

Adds ``src/ai`` to ``sys.path`` so ``import app.main`` works regardless of the
cwd pytest is invoked from.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

# --- sys.path shim ---------------------------------------------------------
_SRC_AI = Path(__file__).resolve().parent.parent
if str(_SRC_AI) not in sys.path:
    sys.path.insert(0, str(_SRC_AI))


import numpy as np  # noqa: E402
import pytest  # noqa: E402
from PIL import Image  # noqa: E402


@pytest.fixture
def photo_root(tmp_path, monkeypatch) -> Path:
    """Isolate the AI_PHOTO_ROOT to a tmp dir so path-escape tests are clean."""
    root = tmp_path / "photos"
    root.mkdir()
    monkeypatch.setenv("AI_PHOTO_ROOT", str(root))
    return root


@pytest.fixture
def sample_jpeg(photo_root) -> Path:
    """Write a small 200x120 RGB JPEG with a known dominant colour.

    The image is mostly beige-ish so ColorExtractor has an unambiguous
    dominant cluster.
    """
    arr = np.full((120, 200, 3), fill_value=(232, 217, 176), dtype=np.uint8)
    # Scatter a few pixels of a different colour so k-means has >1 cluster.
    arr[0:10, 0:10] = (40, 40, 40)
    img = Image.fromarray(arr, mode="RGB")
    out = photo_root / "sample.jpg"
    img.save(out, format="JPEG", quality=90)
    return out


@pytest.fixture
def not_an_image(photo_root) -> Path:
    """Produce a text file disguised as .jpg (AC-7)."""
    p = photo_root / "fake.jpg"
    p.write_bytes(b"this is definitely not a JPEG")
    return p
