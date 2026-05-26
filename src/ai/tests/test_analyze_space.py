"""Integration tests for ``POST /analyze/space`` (FR-3, AC-3..AC-10, AC-13, AC-14)."""

from __future__ import annotations

import re

import pytest
from fastapi.testclient import TestClient

from app.main import app


HEX_RE = re.compile(r"^#[0-9A-F]{6}$")


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


# ---------------------------------------------------------------------------
# Health + OpenAPI contract (AC-3, AC-14)
# ---------------------------------------------------------------------------


def test_health_ok(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["service"] == "space-analysis"
    assert isinstance(body["version"], str) and body["version"]


def test_openapi_exposes_space_routes(client):
    """Task-4 override of Task-3 AC-14 — /analyze/style is now registered.

    Task 4 (UC-01-style-selection, FR-1 / AC-1) wires ``/analyze/style``
    onto the same FastAPI app. Task-3's original AC-14 (which asserted
    /analyze/style was ABSENT) is explicitly overridden here — see
    Task-4 AC-36 / AC-1.
    """
    r = client.get("/openapi.json")
    assert r.status_code == 200
    paths = r.json().get("paths", {})
    assert "/health" in paths
    assert "/analyze/space" in paths
    assert "/analyze/style" in paths


# ---------------------------------------------------------------------------
# Happy path + determinism (AC-4, AC-5)
# ---------------------------------------------------------------------------


def test_analyze_happy_path(client, sample_jpeg):
    body = {
        "roomId": "test-ac4",
        "photoUrl": sample_jpeg.as_uri(),
    }
    r = client.post("/analyze/space", json=body)
    assert r.status_code == 200, r.text
    data = r.json()

    assert data["roomId"] == "test-ac4"
    assert data["status"] == "OK"
    d = data["dimensions"]
    assert 2.5 <= d["widthM"] <= 6.0
    assert 2.5 <= d["lengthM"] <= 6.0
    assert d["heightM"] == 2.4
    assert d["areaM2"] == round(d["widthM"] * d["lengthM"], 2)
    assert HEX_RE.match(data["mainColor"]), data["mainColor"]
    assert 0.0 <= data["confidence"] <= 1.0
    assert data["processingMs"] > 0

    # UC-ML-PERSIST AC-3 / AC-5 — detections + image dims are part of the
    # success contract. The synthetic fixture may contain zero detectable
    # furniture, so we assert the SHAPE (present, correctly typed) here and
    # leave element-content assertions to the analyzer unit test.
    assert isinstance(data["imageWidth"], int) and data["imageWidth"] >= 1
    assert isinstance(data["imageHeight"], int) and data["imageHeight"] >= 1
    assert "detections" in data
    assert isinstance(data["detections"], list)
    for det in data["detections"]:
        assert isinstance(det["label"], str)
        assert len(det["bbox"]) == 4
        assert all(isinstance(n, (int, float)) for n in det["bbox"])
        assert 0.0 <= det["confidence"] <= 1.0


def test_analyze_is_deterministic(client, sample_jpeg):
    body = {"roomId": "determ", "photoUrl": sample_jpeg.as_uri()}
    r1 = client.post("/analyze/space", json=body).json()
    r2 = client.post("/analyze/space", json=body).json()
    assert r1["dimensions"] == r2["dimensions"]
    assert r1["mainColor"] == r2["mainColor"]
    assert r1["confidence"] == r2["confidence"]


# ---------------------------------------------------------------------------
# Error paths (AC-6, AC-7, AC-8, AC-9)
# ---------------------------------------------------------------------------


def test_image_not_found(client, photo_root):
    body = {
        "roomId": "nf",
        "photoUrl": (photo_root / "missing.jpg").as_uri(),
    }
    r = client.post("/analyze/space", json=body)
    assert r.status_code == 422
    data = r.json()
    assert data["errorCode"] == "IMAGE_NOT_FOUND"
    assert data["roomId"] == "nf"


def test_image_read_failed(client, not_an_image):
    body = {
        "roomId": "rf",
        "photoUrl": not_an_image.as_uri(),
    }
    r = client.post("/analyze/space", json=body)
    assert r.status_code == 422
    # ColorExtractor raises IMAGE_READ_FAILED for undecodable bytes.
    assert r.json()["errorCode"] == "IMAGE_READ_FAILED"


def test_invalid_request_missing_room_id(client, sample_jpeg):
    body = {"photoUrl": sample_jpeg.as_uri()}  # no roomId
    r = client.post("/analyze/space", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "INVALID_REQUEST"


def test_invalid_request_bad_scheme(client):
    body = {"roomId": "x1", "photoUrl": "http://example.com/foo.jpg"}
    r = client.post("/analyze/space", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "INVALID_REQUEST"


def test_path_escape_is_image_not_found(client, photo_root, tmp_path):
    """AC-9 — a path outside AI_PHOTO_ROOT must NOT leak INTERNAL_ERROR."""
    # Write a file OUTSIDE the photo root and point at it via traversal.
    outside = tmp_path / "outside.jpg"
    outside.write_bytes(b"whatever")
    body = {"roomId": "esc", "photoUrl": outside.as_uri()}
    r = client.post("/analyze/space", json=body)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "IMAGE_NOT_FOUND"
