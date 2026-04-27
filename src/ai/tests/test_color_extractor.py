"""Unit tests for ``ColorExtractor`` (AC-11, AC-12)."""

from __future__ import annotations

import re

import numpy as np
import pytest
from PIL import Image

from app.analyzers.color import ColorExtractor
from app.errors import ImageNotFoundError, ImageReadError


HEX_RE = re.compile(r"^#[0-9A-F]{6}$")


def test_real_cv2_kmeans_extracts_dominant_hex(tmp_path):
    """ColorExtractor returns a uppercase hex and high confidence for a mono image."""
    # Nearly-uniform beige image — dominant cluster should be overwhelming.
    arr = np.full((80, 80, 3), fill_value=(232, 217, 176), dtype=np.uint8)
    arr[0:2, 0:2] = (0, 0, 0)  # tiny noise
    path = tmp_path / "beige.jpg"
    Image.fromarray(arr, mode="RGB").save(path, format="JPEG", quality=95)

    result = ColorExtractor().analyze(path)

    assert HEX_RE.match(result.mainColor), result.mainColor
    # Dominant cluster covers >95% of pixels in an image this uniform.
    assert result.confidence >= 0.9


def test_missing_file_raises_not_found(tmp_path):
    """AC-6 analog at the analyzer layer."""
    with pytest.raises(ImageNotFoundError):
        ColorExtractor().analyze(tmp_path / "nope.jpg")


def test_non_image_raises_read_failed(tmp_path):
    """AC-7 analog at the analyzer layer."""
    p = tmp_path / "fake.jpg"
    p.write_bytes(b"not an image")
    with pytest.raises(ImageReadError):
        ColorExtractor().analyze(p)


def test_deterministic_on_identical_input(tmp_path):
    """AC-5 building block — same bytes yield same hex + confidence."""
    arr = np.zeros((40, 40, 3), dtype=np.uint8)
    arr[:, :] = (17, 88, 199)
    path = tmp_path / "blue.jpg"
    Image.fromarray(arr, mode="RGB").save(path, format="JPEG", quality=95)

    r1 = ColorExtractor().analyze(path)
    r2 = ColorExtractor().analyze(path)
    assert r1.mainColor == r2.mainColor
    assert r1.confidence == r2.confidence
