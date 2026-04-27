"""Integration tests for ``POST /analyze/style`` (FR-1..FR-7, AC-1..AC-8)."""

from __future__ import annotations

import math
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.analyzers.style import STYLE_LABELS
from app.main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


# ---------------------------------------------------------------------------
# AC-1 — OpenAPI exposes both routes, no stale TODO comment
# ---------------------------------------------------------------------------


def test_openapi_registers_style_route(client):
    r = client.get("/openapi.json")
    assert r.status_code == 200
    paths = r.json().get("paths", {})
    assert "/analyze/space" in paths
    assert "/analyze/style" in paths


def test_main_has_no_task4_todo_comment():
    """AC-1 — the placeholder TODO(task-4) comment is removed."""
    main_py = (
        Path(__file__).resolve().parent.parent / "app" / "main.py"
    ).read_text(encoding="utf-8")
    assert "TODO(task-4)" not in main_py


# ---------------------------------------------------------------------------
# AC-2 — happy-path response shape
# ---------------------------------------------------------------------------


def test_analyze_style_happy_path(client, sample_jpeg):
    body = {"roomId": "t2", "photoUrl": sample_jpeg.as_uri()}
    r = client.post("/analyze/style", json=body)
    assert r.status_code == 200, r.text
    data = r.json()

    assert data["roomId"] == "t2"
    assert data["status"] == "OK"
    assert data["style"] in set(STYLE_LABELS)
    assert 0.0 <= data["confidence"] <= 1.0
    assert set(data["scores"].keys()) == set(STYLE_LABELS)

    total = sum(data["scores"].values())
    assert 0.99 <= total <= 1.01

    argmax_label = max(data["scores"], key=lambda k: data["scores"][k])
    assert argmax_label == data["style"]
    assert math.isclose(data["scores"][data["style"]], data["confidence"], abs_tol=0.01)
    assert data["processingMs"] > 0


# ---------------------------------------------------------------------------
# AC-3 — determinism
# ---------------------------------------------------------------------------


def test_analyze_style_is_deterministic(client, sample_jpeg):
    body = {"roomId": "determ-style", "photoUrl": sample_jpeg.as_uri()}
    r1 = client.post("/analyze/style", json=body).json()
    r2 = client.post("/analyze/style", json=body).json()
    assert r1["style"] == r2["style"]
    assert r1["confidence"] == r2["confidence"]
    assert r1["scores"] == r2["scores"]


# ---------------------------------------------------------------------------
# AC-4 — CURRENT is never emitted and outputs are strictly within enum
# ---------------------------------------------------------------------------


def test_analyze_style_never_emits_current(client, photo_root):
    """Generate 50 hash-varied JPEGs and confirm every style is in the 5-value enum."""
    for i in range(50):
        # Different pixels → different bytes → different sha256 → different argmax.
        arr = np.full((40, 60, 3), (i * 3 % 255, (i * 5) % 255, (i * 7) % 255), dtype=np.uint8)
        # Add a unique marker so JPEG compression still yields distinct bytes.
        arr[0:2, 0:2] = (i, i * 2 % 255, i * 3 % 255)
        p = photo_root / f"fuzz_{i}.jpg"
        Image.fromarray(arr, mode="RGB").save(p, format="JPEG", quality=80)

        body = {"roomId": f"fz{i}", "photoUrl": p.as_uri()}
        r = client.post("/analyze/style", json=body)
        assert r.status_code == 200, r.text
        style = r.json()["style"]
        assert style != "CURRENT"
        assert style in set(STYLE_LABELS)


# ---------------------------------------------------------------------------
# AC-5 — error envelopes match UC-01-space-analysis exactly
# ---------------------------------------------------------------------------


def test_style_image_not_found(client, photo_root):
    body = {"roomId": "nf", "photoUrl": (photo_root / "ghost.jpg").as_uri()}
    r = client.post("/analyze/style", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "IMAGE_NOT_FOUND"


def test_style_image_read_failed(client, photo_root):
    # The hash-based classifier only needs raw bytes, so non-JPEG
    # payloads still hash successfully. The deterministic trigger for
    # IMAGE_READ_FAILED is an EMPTY file (classifier raises on zero bytes).
    empty = photo_root / "empty.jpg"
    empty.write_bytes(b"")
    body = {"roomId": "rf", "photoUrl": empty.as_uri()}
    r = client.post("/analyze/style", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "IMAGE_READ_FAILED"


def test_style_invalid_request_missing_room_id(client, sample_jpeg):
    body = {"photoUrl": sample_jpeg.as_uri()}
    r = client.post("/analyze/style", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "INVALID_REQUEST"


def test_style_invalid_request_bad_scheme(client):
    body = {"roomId": "x1", "photoUrl": "http://example.com/foo.jpg"}
    r = client.post("/analyze/style", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "INVALID_REQUEST"


def test_style_path_escape_is_image_not_found(client, photo_root, tmp_path):
    outside = tmp_path / "escape.jpg"
    outside.write_bytes(b"escape bytes")
    body = {"roomId": "esc", "photoUrl": outside.as_uri()}
    r = client.post("/analyze/style", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "IMAGE_NOT_FOUND"


# ---------------------------------------------------------------------------
# AC-6 — handler is async + uses asyncio.to_thread
# ---------------------------------------------------------------------------


def test_style_route_is_async_and_offloads_classifier():
    route_py = (
        Path(__file__).resolve().parent.parent / "app" / "routes" / "style.py"
    ).read_text(encoding="utf-8")
    assert "async def analyze_style" in route_py
    assert "asyncio.to_thread" in route_py


# ---------------------------------------------------------------------------
# AC-8 — StyleClassifier docstring advertises placeholder + future model
# ---------------------------------------------------------------------------


def test_style_classifier_has_placeholder_docstring():
    src = (
        Path(__file__).resolve().parent.parent / "app" / "analyzers" / "style.py"
    ).read_text(encoding="utf-8")
    assert "TODO(ml)" in src
    # at least one of the future-model hints required by AC-8
    assert any(name in src for name in ("ResNet", "CLIP", "transformer", "classifier"))
