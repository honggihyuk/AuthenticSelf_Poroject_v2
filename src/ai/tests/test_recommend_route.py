"""Integration tests for ``POST /recommend/furniture``
(UC-01-recommendation FR-1..FR-12 / AC-1..AC-23).

Every AC that is test_type=integration for the Python side lives here.
The scorer is exercised via the real HTTP path so we also cover
contract validation, async handler declaration, and error envelopes.
"""

from __future__ import annotations

from pathlib import Path
from typing import List

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


# --------------------------------------------------------------------------
# Catalog factory — 24 rows across desk/bed/chair/lighting with variety.
# --------------------------------------------------------------------------


def _item(
    fid: str,
    ftype: str,
    name: str = "",
    tags=("MODERN",),
    w=120,
    l=60,
    h=74,
    color="#E8D9B0",
    price=100000,
) -> dict:
    return {
        "furnitureId": fid,
        "type": ftype,
        "name": name or fid,
        "styleTags": list(tags),
        "widthCm": w,
        "lengthCm": l,
        "heightCm": h,
        "colorHex": color,
        "price": price,
        "imageUrl": f"https://cdn.example.com/{fid}.jpg",
    }


def _build_24_item_catalog() -> List[dict]:
    # Six per category, styles varied across MODERN / SIMPLE / CLASSIC /
    # SCANDINAVIAN / INDUSTRIAL so style-match scoring exercises each branch.
    #
    # Keep every item well under 370 cm on each side so they all hard-fit
    # in the AC-2 reference room (4x4x2.4 m). Heights <= 200 so the
    # lighting's oversize row does NOT appear here (the FR-16 seed data
    # includes oversize rows; this factory is for AC-2 which needs items
    # to fit).
    items: List[dict] = []

    items.append(_item("f_desk_001", "desk", tags=("MODERN",),       w=120, l=60, h=74,  color="#F3E6D2", price=189000))
    items.append(_item("f_desk_002", "desk", tags=("SCANDINAVIAN",), w=140, l=70, h=74,  color="#E8D9B0", price=215000))
    items.append(_item("f_desk_003", "desk", tags=("CLASSIC",),      w=160, l=80, h=76,  color="#5A3A1E", price=310000))
    items.append(_item("f_desk_004", "desk", tags=("INDUSTRIAL",),   w=140, l=70, h=74,  color="#3C3C3C", price=245000))
    items.append(_item("f_desk_005", "desk", tags=("SIMPLE",),       w=100, l=55, h=72,  color="#FFFFFF", price=155000))
    items.append(_item("f_desk_006", "desk", tags=("MODERN", "SCANDINAVIAN"), w=130, l=65, h=74, color="#DED2B0", price=205000))

    items.append(_item("f_bed_001",  "bed",  tags=("MODERN",),       w=220, l=160, h=90, color="#EDE0C8", price=680000))
    items.append(_item("f_bed_002",  "bed",  tags=("SCANDINAVIAN",), w=210, l=150, h=85, color="#F2E8D2", price=720000))
    items.append(_item("f_bed_003",  "bed",  tags=("CLASSIC",),      w=230, l=170, h=110, color="#5A3A1E", price=890000))
    items.append(_item("f_bed_004",  "bed",  tags=("INDUSTRIAL",),   w=220, l=160, h=95, color="#2E2E2E", price=740000))
    items.append(_item("f_bed_005",  "bed",  tags=("SIMPLE",),       w=200, l=150, h=80, color="#F0F0F0", price=610000))
    items.append(_item("f_bed_006",  "bed",  tags=("MODERN", "SIMPLE"), w=220, l=160, h=85, color="#E2D6B4", price=665000))

    items.append(_item("f_chair_001", "chair", tags=("MODERN",),        w=55, l=55, h=80, color="#2B2B2B", price=89000))
    items.append(_item("f_chair_002", "chair", tags=("SCANDINAVIAN",),  w=60, l=60, h=82, color="#F1E2C4", price=99000))
    items.append(_item("f_chair_003", "chair", tags=("CLASSIC",),       w=58, l=58, h=92, color="#5A3A1E", price=125000))
    items.append(_item("f_chair_004", "chair", tags=("INDUSTRIAL",),    w=55, l=55, h=86, color="#202020", price=119000))
    items.append(_item("f_chair_005", "chair", tags=("SIMPLE",),        w=50, l=50, h=80, color="#FFFFFF", price=79000))
    items.append(_item("f_chair_006", "chair", tags=("MODERN", "INDUSTRIAL"), w=55, l=55, h=82, color="#3A3A3A", price=105000))

    items.append(_item("f_lighting_001", "lighting", tags=("MODERN",),       w=35, l=35, h=160, color="#F4EAD0", price=135000))
    items.append(_item("f_lighting_002", "lighting", tags=("SCANDINAVIAN",), w=30, l=30, h=150, color="#FFFFFF", price=125000))
    items.append(_item("f_lighting_003", "lighting", tags=("CLASSIC",),      w=40, l=40, h=170, color="#8A6A3A", price=165000))
    items.append(_item("f_lighting_004", "lighting", tags=("INDUSTRIAL",),   w=35, l=35, h=160, color="#2A2A2A", price=145000))
    items.append(_item("f_lighting_005", "lighting", tags=("SIMPLE",),       w=30, l=30, h=140, color="#EEEEEE", price=99000))
    items.append(_item("f_lighting_006", "lighting", tags=("MODERN", "SIMPLE"), w=32, l=32, h=155, color="#E2D6B4", price=115000))

    return items


def _base_request(catalog: List[dict] | None = None) -> dict:
    return {
        "roomId": "r_integ_01",
        "userId": "u1",
        "space": {
            "dimensions": {"widthM": 4.0, "lengthM": 4.0, "heightM": 2.4},
            "mainColor": "#E8D9B0",
            "detectedStyle": "MODERN",
            "detectedObjects": [],
        },
        "preferredStyle": "MODERN",
        "catalog": catalog if catalog is not None else _build_24_item_catalog(),
        "topNPerCategory": 3,
    }


# --------------------------------------------------------------------------
# AC-1 — /openapi.json contains /recommend/furniture alongside analyze routes
# --------------------------------------------------------------------------


def test_openapi_registers_recommend_route(client):
    r = client.get("/openapi.json")
    assert r.status_code == 200
    paths = r.json().get("paths", {})
    assert "/analyze/space" in paths
    assert "/analyze/style" in paths
    assert "/recommend/furniture" in paths


# --------------------------------------------------------------------------
# AC-2 — happy path response shape
# --------------------------------------------------------------------------


def test_recommend_happy_path_ac2(client):
    r = client.post("/recommend/furniture", json=_base_request())
    assert r.status_code == 200, r.text
    body = r.json()

    assert body["roomId"] == "r_integ_01"
    assert body["status"] == "OK"
    assert body["resolvedStyle"] == "MODERN"

    # Exactly the four keys, no extras (FR-3).
    assert set(body["recommendations"].keys()) == {"desk", "bed", "chair", "lighting"}

    # Each array <= topNPerCategory=3, every item has the 8 required fields.
    required_item_keys = {
        "furnitureId", "name", "type", "price", "imageUrl",
        "fitScore", "scoreBreakdown", "rationale",
    }
    required_breakdown = {"sizeFit", "styleMatch", "colorHarmony", "objectConflict"}

    for cat in ("desk", "bed", "chair", "lighting"):
        arr = body["recommendations"][cat]
        assert len(arr) <= 3
        for it in arr:
            assert required_item_keys.issubset(set(it.keys()))
            assert required_breakdown == set(it["scoreBreakdown"].keys())

    assert body["processingMs"] > 0
    assert body["warning"] is None


# --------------------------------------------------------------------------
# AC-3 — determinism across three identical calls
# --------------------------------------------------------------------------


def test_recommend_determinism_ac3(client):
    req = _base_request()
    responses = [client.post("/recommend/furniture", json=req).json() for _ in range(3)]
    # Strip processingMs/generatedAt (vary between calls), then compare.
    for r in responses:
        r.pop("processingMs", None)
        r.pop("generatedAt", None)
    assert responses[0] == responses[1] == responses[2]


# --------------------------------------------------------------------------
# AC-4 — zero-item category still yields an empty array key
# --------------------------------------------------------------------------


def test_recommend_zero_beds_ac4(client):
    catalog = [c for c in _build_24_item_catalog() if c["type"] != "bed"]
    req = _base_request(catalog=catalog)
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 200
    recs = r.json()["recommendations"]
    assert recs["bed"] == []
    # No additional keys appeared.
    assert set(recs.keys()) == {"desk", "bed", "chair", "lighting"}


# --------------------------------------------------------------------------
# AC-5 — oversize desk is hard-fit-excluded (via the route end-to-end)
# --------------------------------------------------------------------------


def test_recommend_hard_fit_excludes_ac5(client):
    oversize = _item("f_desk_huge", "desk", w=500, l=200, h=100)
    catalog = [oversize] + _build_24_item_catalog()
    req = _base_request(catalog=catalog)
    req["space"]["dimensions"] = {"widthM": 3.0, "lengthM": 3.0, "heightM": 2.4}
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 200
    desk_ids = [i["furnitureId"] for i in r.json()["recommendations"]["desk"]]
    assert "f_desk_huge" not in desk_ids


# --------------------------------------------------------------------------
# AC-16 / AC-17 / AC-18 — resolvedStyle derivation
# --------------------------------------------------------------------------


def test_resolved_style_current_uses_detected_ac16(client):
    req = _base_request()
    req["preferredStyle"] = "CURRENT"
    req["space"]["detectedStyle"] = "INDUSTRIAL"
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 200
    assert r.json()["resolvedStyle"] == "INDUSTRIAL"


def test_resolved_style_current_null_fallback_ac17(client):
    req = _base_request()
    req["preferredStyle"] = "CURRENT"
    req["space"]["detectedStyle"] = None
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 200
    assert r.json()["resolvedStyle"] == "MODERN"


def test_resolved_style_explicit_wins_ac18(client):
    req = _base_request()
    req["preferredStyle"] = "SIMPLE"
    req["space"]["detectedStyle"] = "INDUSTRIAL"
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 200
    assert r.json()["resolvedStyle"] == "SIMPLE"


# --------------------------------------------------------------------------
# AC-19 — NO_FIT_ANY_CATEGORY warning when no item fits
# --------------------------------------------------------------------------


def test_no_fit_any_category_ac19(client):
    # Tiny room (1.5x1.5). Every catalog item with longer side ≥ 140 cm
    # fails the hard fit check (max dim ≤ 150-30 = 120).
    catalog = [
        _item("f_desk_big", "desk",     w=140, l=80,  h=75),
        _item("f_bed_big",  "bed",      w=210, l=150, h=85),
        _item("f_chair_big","chair",    w=150, l=80,  h=80),
        _item("f_lighting_big","lighting", w=140, l=140, h=150),
    ]
    req = _base_request(catalog=catalog)
    req["space"]["dimensions"] = {"widthM": 1.5, "lengthM": 1.5, "heightM": 2.4}
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 200
    body = r.json()
    assert body["warning"] == "NO_FIT_ANY_CATEGORY"
    for cat in ("desk", "bed", "chair", "lighting"):
        assert body["recommendations"][cat] == []


# --------------------------------------------------------------------------
# AC-20 — CATALOG_EMPTY 422
# --------------------------------------------------------------------------


def test_catalog_empty_ac20(client):
    req = _base_request(catalog=[])
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 422
    body = r.json()
    assert body["errorCode"] == "CATALOG_EMPTY"
    assert body["roomId"] == "r_integ_01"
    assert isinstance(body["message"], str) and body["message"]


# --------------------------------------------------------------------------
# AC-21 — INVALID_REQUEST on bogus enum
# --------------------------------------------------------------------------


def test_invalid_preferred_style_ac21(client):
    req = _base_request()
    req["preferredStyle"] = "BAROQUE"
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "INVALID_REQUEST"


def test_invalid_request_missing_room_id(client):
    req = _base_request()
    del req["roomId"]
    r = client.post("/recommend/furniture", json=req)
    assert r.status_code == 422
    assert r.json()["errorCode"] == "INVALID_REQUEST"


# --------------------------------------------------------------------------
# AC-22 — handler is async def (static file inspection)
# --------------------------------------------------------------------------


def test_handler_is_async_def_ac22():
    route_py = (
        Path(__file__).resolve().parent.parent / "app" / "routes" / "recommend.py"
    ).read_text(encoding="utf-8")
    assert "async def recommend_furniture" in route_py
    assert "asyncio.to_thread" in route_py


# --------------------------------------------------------------------------
# AC-23 — no heavy ML deps in runtime. Re-assert Task 3's policy.
# --------------------------------------------------------------------------


def test_no_heavy_ml_deps_ac23():
    pyproj = (
        Path(__file__).resolve().parent.parent / "pyproject.toml"
    ).read_text(encoding="utf-8")
    # Bound the region we check to the [project.dependencies] block so we
    # don't false-positive on a comment like "# no tensorflow here".
    start = pyproj.index("dependencies = [")
    end = pyproj.index("]", start)
    runtime_deps_block = pyproj[start:end].lower()
    for forbidden in ("torch", "tensorflow", "keras", "transformers", "ultralytics"):
        assert forbidden not in runtime_deps_block, \
            f"'{forbidden}' must not be in runtime deps (AC-23)."


# --------------------------------------------------------------------------
# AC-39 flavour — top-1 of each category has styleMatch >= 0.50
# (mirror of the Spring-side AC; verifying the Python output too.)
# --------------------------------------------------------------------------


def test_top1_style_match_invariant(client):
    r = client.post("/recommend/furniture", json=_base_request())
    assert r.status_code == 200
    recs = r.json()["recommendations"]
    for cat in ("desk", "bed", "chair", "lighting"):
        if recs[cat]:
            top = recs[cat][0]
            assert top["scoreBreakdown"]["styleMatch"] >= 0.50, \
                f"top-1 of {cat} must have styleMatch >= 0.50 (got {top!r})"
