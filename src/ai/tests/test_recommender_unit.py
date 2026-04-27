"""Unit tests for the pure-function scorer (FR-5..FR-10 / AC-6..AC-15).

These test the :mod:`app.analyzers.recommender` module directly — no
FastAPI, no pydantic — because every acceptance criterion in AC-6..AC-15
is expressed against the rule-based scoring function itself.
"""

from __future__ import annotations

from app.analyzers.recommender import (
    FurnitureItem,
    RoomDims,
    DetectedObject,
    W_SIZE,
    W_STYLE,
    W_COLOR,
    W_CONFLICT,
    compose_rationale,
    rank,
    resolve_style,
    score_color_harmony,
    score_object_conflict,
    score_size_fit,
    score_style_match,
    score_item,
)


# --------------------------------------------------------------------------
# Fixtures (cheap to build, so just a helper)
# --------------------------------------------------------------------------


def room_4x4() -> RoomDims:
    return RoomDims(widthM=4.0, lengthM=4.0, heightM=2.4)


def make_item(
    fid: str = "f_desk_001",
    ftype: str = "desk",
    name: str = "Oslo Slim Desk",
    style_tags=("MODERN",),
    w_cm: int = 120,
    l_cm: int = 60,
    h_cm: int = 74,
    color_hex: str = "#F3E6D2",
    price: int = 189000,
) -> FurnitureItem:
    return FurnitureItem(
        furnitureId=fid,
        type=ftype,
        name=name,
        styleTags=tuple(style_tags),
        widthCm=w_cm,
        lengthCm=l_cm,
        heightCm=h_cm,
        colorHex=color_hex,
        price=price,
        imageUrl=f"https://cdn.example.com/{fid}.jpg",
    )


# --------------------------------------------------------------------------
# AC-15 — weights are literally 0.35/0.30/0.20/0.15 and sum to 1.0.
# --------------------------------------------------------------------------


def test_weights_fixed_and_sum_to_one_ac15():
    # Exact float values — AC-15 is literal-grep-friendly.
    assert W_SIZE == 0.35
    assert W_STYLE == 0.30
    assert W_COLOR == 0.20
    assert W_CONFLICT == 0.15
    assert round(W_SIZE + W_STYLE + W_COLOR + W_CONFLICT, 6) == 1.0


# --------------------------------------------------------------------------
# AC-6 — sizeFit banding
# --------------------------------------------------------------------------


def test_size_fit_15pct_is_1_0_ac6():
    # 4x4m => 400x400 cm => area = 160,000 cm^2. 15% == 24,000 cm^2.
    # A 200x120 desk => 24,000 exactly. Hard-fit passes (max dim 200 <= 370).
    item = make_item(w_cm=200, l_cm=120, h_cm=74)
    score, ok = score_size_fit(item, room_4x4())
    assert ok is True
    assert score == 1.0


def test_size_fit_25pct_is_0_7_ac6():
    # 25% of 160,000 == 40,000. A 200x200 desk => 40,000.
    item = make_item(w_cm=200, l_cm=200, h_cm=74)
    score, ok = score_size_fit(item, room_4x4())
    assert ok is True
    assert score == 0.7


def test_size_fit_45pct_is_0_4_ac6():
    # 45% of 160,000 == 72,000. A 300x240 desk => 72,000.
    item = make_item(w_cm=300, l_cm=240, h_cm=74)
    score, ok = score_size_fit(item, room_4x4())
    assert ok is True
    assert score == 0.4


# --------------------------------------------------------------------------
# AC-5 — hard-fit exclusion
# --------------------------------------------------------------------------


def test_size_fit_hard_fail_oversize_ac5():
    small_room = RoomDims(widthM=3.0, lengthM=3.0, heightM=2.4)
    # max(500, 200) = 500 > 300-30 = 270.
    item = make_item(w_cm=500, l_cm=200, h_cm=100)
    score, ok = score_size_fit(item, small_room)
    assert ok is False
    assert score == 0.0


def test_size_fit_hard_fail_too_tall():
    small_room = RoomDims(widthM=3.0, lengthM=3.0, heightM=2.4)
    # height 230 > 240-20 = 220.
    item = make_item(w_cm=100, l_cm=60, h_cm=230)
    score, ok = score_size_fit(item, small_room)
    assert ok is False
    assert score == 0.0


# --------------------------------------------------------------------------
# AC-7 / AC-8 / AC-9 — style match
# --------------------------------------------------------------------------


def test_style_match_exact_ac7():
    assert score_style_match("MODERN", ["MODERN", "SCANDINAVIAN"]) == 1.00


def test_style_match_compatible_pair_ac8():
    assert score_style_match("MODERN", ["SIMPLE"]) == 0.50
    # symmetric
    assert score_style_match("SIMPLE", ["MODERN"]) == 0.50
    # another pair
    assert score_style_match("CLASSIC", ["INDUSTRIAL"]) == 0.50


def test_style_match_incompatible_ac9():
    assert score_style_match("MODERN", ["CLASSIC"]) == 0.10
    assert score_style_match("SCANDINAVIAN", ["INDUSTRIAL"]) == 0.10


# --------------------------------------------------------------------------
# AC-10 / AC-11 — color harmony
# --------------------------------------------------------------------------


def test_color_harmony_analogous_ac10():
    # Both beige; hues very close.
    assert score_color_harmony("#E8D9B0", "#E8C9A0") >= 0.85


def test_color_harmony_clashing_ac11():
    # Beige vs deep blue-cyan; hue delta ~160 degrees.
    # Beige is saturated enough (not near-neutral) for the raw bucket to apply.
    score = score_color_harmony("#E8D9B0", "#106090")
    assert score <= 0.55


def test_color_harmony_neutrals_bump():
    # Pure grey (saturation 0) paired with a strong hue still scores well
    # because the neutral bump is applied.
    score = score_color_harmony("#808080", "#FF0000")
    assert score >= 0.55


# --------------------------------------------------------------------------
# AC-12 / AC-13 — object conflict
# --------------------------------------------------------------------------


def test_object_conflict_bed_detected_ac12():
    bed_item = make_item(ftype="bed")
    desk_item = make_item(ftype="desk")
    det = [DetectedObject(type="bed", bboxNorm=(0.1, 0.4, 0.6, 0.9), confidence=0.82)]
    assert score_object_conflict(bed_item, det) == 0.20
    assert score_object_conflict(desk_item, det) == 1.00


def test_object_conflict_low_confidence_not_triggering():
    bed_item = make_item(ftype="bed")
    det = [DetectedObject(type="bed", bboxNorm=(0.1, 0.4, 0.6, 0.9), confidence=0.40)]
    # Below 0.5 confidence — does NOT trigger the penalty.
    assert score_object_conflict(bed_item, det) == 1.00


def test_object_conflict_lighting_never_penalized_ac13():
    lighting_item = make_item(ftype="lighting")
    det = [DetectedObject(type="lighting", bboxNorm=(0.0, 0.0, 0.1, 0.1), confidence=0.95)]
    assert score_object_conflict(lighting_item, det) == 1.00


# --------------------------------------------------------------------------
# AC-14 — ranking tie-breaker (fitScore DESC, price ASC, furnitureId ASC)
# --------------------------------------------------------------------------


def test_ranking_tiebreaker_price_then_id_ac14():
    # Two desks with identical style / colour / size => identical fitScore.
    a = make_item(fid="f_desk_B", price=200000)
    b = make_item(fid="f_desk_A", price=150000)
    c = make_item(fid="f_desk_C", price=150000)

    buckets = rank(
        [a, b, c],
        resolved_style="MODERN",
        room=room_4x4(),
        room_color_hex="#E8D9B0",
        detected_objects=[],
        top_n_per_category=3,
    )
    ids = [s.furnitureId for s in buckets["desk"]]
    # b and c tie on price; b has the lexicographically smaller furnitureId.
    assert ids == ["f_desk_A", "f_desk_C", "f_desk_B"]


# --------------------------------------------------------------------------
# resolve_style (FR-4 / AC-16..AC-18 flavoured unit slice)
# --------------------------------------------------------------------------


def test_resolve_style_current_uses_detected():
    assert resolve_style("CURRENT", "INDUSTRIAL") == "INDUSTRIAL"


def test_resolve_style_current_null_fallback():
    assert resolve_style("CURRENT", None) == "MODERN"


def test_resolve_style_explicit_wins():
    # Explicit user choice overrides the detected style entirely.
    assert resolve_style("SIMPLE", "INDUSTRIAL") == "SIMPLE"


# --------------------------------------------------------------------------
# compose_rationale (FR-10 smoke checks)
# --------------------------------------------------------------------------


def test_compose_rationale_happy_path():
    s = compose_rationale(
        resolved_style="MODERN",
        size_fit=1.0,
        style_match=1.00,
        color_harmony=0.85,
        object_conflict=1.00,
    )
    assert "모던 스타일 일치" in s
    assert "여유롭게" in s
    assert "컬러 조화 우수" in s
    assert len(s) <= 120


def test_compose_rationale_conflict_badge():
    s = compose_rationale(
        resolved_style="MODERN",
        size_fit=0.7,
        style_match=0.50,
        color_harmony=0.55,
        object_conflict=0.20,
    )
    assert "호환 스타일" in s
    assert "중복 가능성" in s


def test_compose_rationale_truncates_at_120():
    long_label = "모던"
    s = compose_rationale(
        resolved_style="MODERN",
        size_fit=0.1,
        style_match=0.10,
        color_harmony=0.40,
        object_conflict=0.20,
    )
    assert len(s) <= 120


# --------------------------------------------------------------------------
# Hard-fit exclusion propagates through the full score_item pipeline.
# --------------------------------------------------------------------------


def test_score_item_oversize_returns_none():
    small_room = RoomDims(widthM=3.0, lengthM=3.0, heightM=2.4)
    giant = make_item(w_cm=500, l_cm=200, h_cm=100)
    scored = score_item(
        giant,
        resolved_style="MODERN",
        room=small_room,
        room_color_hex="#E8D9B0",
        detected_objects=[],
    )
    assert scored is None


def test_score_item_happy_path_full_shape():
    item = make_item(style_tags=("MODERN",), color_hex="#E8C9A0")
    scored = score_item(
        item,
        resolved_style="MODERN",
        room=room_4x4(),
        room_color_hex="#E8D9B0",
        detected_objects=[],
    )
    assert scored is not None
    assert scored.fitScore == round(
        W_SIZE * scored.scoreBreakdown.sizeFit
        + W_STYLE * scored.scoreBreakdown.styleMatch
        + W_COLOR * scored.scoreBreakdown.colorHarmony
        + W_CONFLICT * scored.scoreBreakdown.objectConflict,
        2,
    )
    # Rationale should contain Korean style label when exact-match style.
    assert "모던" in scored.rationale
