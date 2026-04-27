"""Rule-based furniture recommender (UC-01-recommendation FR-5..FR-10).

Deterministic, pure-function scorer — no ML weights, no randomness.
Same placeholder-model hygiene rule as Task 3 / Task 4: heavy ML
frameworks (torch/tensorflow/ultralytics) are intentionally kept out
of the runtime-deps set. Color conversion uses the stdlib
:mod:`colorsys` — no new dependency added.

The scoring function is four sub-scores combined with FIXED weights::

    fitScore = round(
        0.35 * sizeFit
      + 0.30 * styleMatch
      + 0.20 * colorHarmony
      + 0.15 * objectConflict,
        2,
    )

Weights are documented-in-source (AC-15) and sum to 1.0 exactly.

Hard-fit exclusion (FR-5) means items that cannot physically fit the
room are dropped from the returned ranking entirely — they are NOT
returned with ``fitScore=0`` because that would pollute the user's top-N.
"""

from __future__ import annotations

import colorsys
from dataclasses import dataclass
from typing import Iterable, List, Mapping, Optional, Sequence, Tuple


# ---------------------------------------------------------------------------
# Fixed weights — DO NOT CHANGE without updating FR-9 / AC-15.
# ---------------------------------------------------------------------------
W_SIZE: float = 0.35
W_STYLE: float = 0.30
W_COLOR: float = 0.20
W_CONFLICT: float = 0.15
# Invariant: W_SIZE + W_STYLE + W_COLOR + W_CONFLICT == 1.0 (AC-15).
assert abs(W_SIZE + W_STYLE + W_COLOR + W_CONFLICT - 1.0) < 1e-9, \
    "Fit-score weights must sum to 1.0 (FR-9 / AC-15)."


# FR-7 style compatibility table (symmetric, intentionally small).
_COMPATIBLE_PAIRS: frozenset[frozenset[str]] = frozenset({
    frozenset({"MODERN", "SCANDINAVIAN"}),
    frozenset({"MODERN", "SIMPLE"}),
    frozenset({"SIMPLE", "SCANDINAVIAN"}),
    frozenset({"CLASSIC", "INDUSTRIAL"}),
})


# FR-10 Korean label map (used in rationale strings).
_STYLE_KO_LABEL: Mapping[str, str] = {
    "MODERN": "모던",
    "SIMPLE": "심플",
    "CLASSIC": "클래식",
    "SCANDINAVIAN": "스칸디나비안",
    "INDUSTRIAL": "인더스트리얼",
}


# ---------------------------------------------------------------------------
# Inputs (plain dataclasses — Pydantic models live in app.schemas_reco)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class RoomDims:
    """Room interior, in metres. Converted to centimetres internally."""
    widthM: float
    lengthM: float
    heightM: float


@dataclass(frozen=True)
class DetectedObject:
    """Object detected in the room photo (type + optional bbox + confidence)."""
    type: str
    bboxNorm: Tuple[float, float, float, float]
    confidence: float


@dataclass(frozen=True)
class FurnitureItem:
    """Catalog item from the Spring side (one row of ``furniture`` table)."""
    furnitureId: str
    type: str                      # "desk" | "bed" | "chair" | "lighting"
    name: str
    styleTags: Tuple[str, ...]     # e.g. ("MODERN", "SCANDINAVIAN")
    widthCm: int
    lengthCm: int
    heightCm: int
    colorHex: str                  # "#RRGGBB"
    price: int                     # integer KRW
    imageUrl: Optional[str]


# ---------------------------------------------------------------------------
# Outputs
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ScoreBreakdown:
    sizeFit: float
    styleMatch: float
    colorHarmony: float
    objectConflict: float


@dataclass(frozen=True)
class ScoredItem:
    furnitureId: str
    name: str
    type: str
    price: int
    imageUrl: Optional[str]
    fitScore: float
    scoreBreakdown: ScoreBreakdown
    rationale: str


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


CATEGORY_KEYS: Tuple[str, ...] = ("desk", "bed", "chair", "lighting")


def resolve_style(preferred_style: str, detected_style: Optional[str]) -> str:
    """FR-4 — derive the effective style used for :func:`score_style_match`.

    ``CURRENT`` means "use the AI-detected style". When the detected
    style is null (Task 4's style analysis transport-failed), fall back
    to ``MODERN`` (documented default).
    """
    if preferred_style == "CURRENT":
        return detected_style if detected_style else "MODERN"
    return preferred_style


def score_size_fit(item: FurnitureItem, room: RoomDims) -> Tuple[float, bool]:
    """FR-5 — return ``(sizeFit, hard_fit_ok)``.

    When ``hard_fit_ok == False``, the caller MUST exclude this item
    from the final ranking (``sizeFit`` is 0.0 in that case, but the
    boolean is the authoritative signal — AC-5 / AC-40).
    """
    room_w = room.widthM * 100.0
    room_l = room.lengthM * 100.0
    room_h = room.heightM * 100.0

    item_long = max(item.widthCm, item.lengthCm)
    item_short = min(item.widthCm, item.lengthCm)

    # Hard fit — 30 cm clearance reserve on the floor, 20 cm on height.
    hard_fit_ok = (
        item_long <= min(room_w, room_l) - 30.0
        and item.heightCm <= room_h - 20.0
        and item_short > 0
        and item.heightCm > 0
    )
    if not hard_fit_ok:
        return 0.0, False

    room_area = room_w * room_l
    if room_area <= 0:
        return 0.0, False
    footprint_ratio = (item.widthCm * item.lengthCm) / room_area

    # Bed bands shift up one tier per FR-5.
    if item.type == "bed":
        if footprint_ratio <= 0.35:
            score = 1.0
        elif footprint_ratio <= 0.50:
            score = 0.7
        elif footprint_ratio <= 0.65:
            score = 0.4
        else:
            score = 0.1
    else:
        if footprint_ratio <= 0.20:
            score = 1.0
        elif footprint_ratio <= 0.35:
            score = 0.7
        elif footprint_ratio <= 0.50:
            score = 0.4
        else:
            score = 0.1

    return score, True


def score_style_match(resolved_style: str, style_tags: Sequence[str]) -> float:
    """FR-7 — 1.00 exact / 0.50 compatible-pair / 0.10 otherwise."""
    tags = set(style_tags)
    if resolved_style in tags:
        return 1.00
    for tag in tags:
        if frozenset({resolved_style, tag}) in _COMPATIBLE_PAIRS:
            return 0.50
    return 0.10


def score_color_harmony(room_hex: str, item_hex: str) -> float:
    """FR-6 — HSL hue-delta bucket + neutral-bump.

    Computes ``hueDelta`` in degrees on the short arc and buckets into
    one of the 5 bands defined in FR-6. Near-neutral hexes (saturation
    < 0.15) bump the score by +0.15 (capped at 1.0) because neutrals
    coexist with most palettes.
    """
    r1, g1, b1 = _hex_to_rgb01(room_hex)
    r2, g2, b2 = _hex_to_rgb01(item_hex)

    h1, l1, s1 = colorsys.rgb_to_hls(r1, g1, b1)
    h2, l2, s2 = colorsys.rgb_to_hls(r2, g2, b2)
    # colorsys returns hue in [0,1]; convert to degrees.
    h1_deg = h1 * 360.0
    h2_deg = h2 * 360.0
    raw_delta = abs(h1_deg - h2_deg)
    hue_delta = min(raw_delta, 360.0 - raw_delta)

    if hue_delta <= 15.0:
        score = 1.00
    elif hue_delta <= 30.0:
        score = 0.85
    elif hue_delta <= 60.0:
        score = 0.70
    elif hue_delta <= 120.0:
        score = 0.55
    else:
        score = 0.40

    # Near-neutral bump (either side).
    if s1 < 0.15 or s2 < 0.15:
        score = min(1.0, score + 0.15)

    return score


def score_object_conflict(
    item: FurnitureItem, detected_objects: Iterable[DetectedObject]
) -> float:
    """FR-8 — 0.20 when a same-type detection exists with confidence ≥ 0.5.

    Exception: lighting items NEVER incur a conflict penalty — the PRD
    explicitly recommends lighting and users often want additional or
    replacement fixtures (FR-8, AC-13).
    """
    if item.type == "lighting":
        return 1.00
    for det in detected_objects:
        if det.type == item.type and det.confidence >= 0.5:
            return 0.20
    return 1.00


def compose_rationale(
    *,
    resolved_style: str,
    size_fit: float,
    style_match: float,
    color_harmony: float,
    object_conflict: float,
) -> str:
    """FR-10 — assemble a Korean rationale from the four sub-scores."""
    parts: List[str] = []

    if style_match == 1.00:
        ko = _STYLE_KO_LABEL.get(resolved_style, resolved_style)
        parts.append(f"{ko} 스타일 일치")
    elif style_match == 0.50:
        parts.append("호환 스타일")

    if size_fit >= 0.7:
        parts.append("공간에 여유롭게 들어맞음")
    elif size_fit >= 0.4:
        parts.append("공간에 딱 맞음")
    else:
        parts.append("공간이 다소 빠듯함")

    if color_harmony >= 0.85:
        parts.append("컬러 조화 우수")
    elif color_harmony >= 0.55:
        parts.append("컬러 조화 양호")
    else:
        parts.append("컬러 대비 강함")

    if object_conflict == 0.20:
        parts.append("(기존 가구와 중복 가능성)")

    joined = ", ".join(parts)
    if len(joined) > 120:
        joined = joined[:119] + "\u2026"   # ellipsis
    return joined


def score_item(
    item: FurnitureItem,
    *,
    resolved_style: str,
    room: RoomDims,
    room_color_hex: str,
    detected_objects: Sequence[DetectedObject],
) -> Optional[ScoredItem]:
    """Score a single catalog item. Returns ``None`` on hard-fit exclusion."""
    size_fit, hard_fit_ok = score_size_fit(item, room)
    if not hard_fit_ok:
        return None

    style_match = score_style_match(resolved_style, item.styleTags)
    color_harmony = score_color_harmony(room_color_hex, item.colorHex)
    object_conflict = score_object_conflict(item, detected_objects)

    fit_score_raw = (
        W_SIZE * size_fit
        + W_STYLE * style_match
        + W_COLOR * color_harmony
        + W_CONFLICT * object_conflict
    )
    fit_score = round(fit_score_raw, 2)

    rationale = compose_rationale(
        resolved_style=resolved_style,
        size_fit=size_fit,
        style_match=style_match,
        color_harmony=color_harmony,
        object_conflict=object_conflict,
    )

    return ScoredItem(
        furnitureId=item.furnitureId,
        name=item.name,
        type=item.type,
        price=item.price,
        imageUrl=item.imageUrl,
        fitScore=fit_score,
        scoreBreakdown=ScoreBreakdown(
            sizeFit=round(size_fit, 2),
            styleMatch=round(style_match, 2),
            colorHarmony=round(color_harmony, 2),
            objectConflict=round(object_conflict, 2),
        ),
        rationale=rationale,
    )


def rank(
    catalog: Sequence[FurnitureItem],
    *,
    resolved_style: str,
    room: RoomDims,
    room_color_hex: str,
    detected_objects: Sequence[DetectedObject],
    top_n_per_category: int,
) -> dict:
    """Score + group + sort + top-N per category.

    Returns a dict with exactly the four keys :data:`CATEGORY_KEYS`.
    Items that fail the hard-fit check are dropped entirely.

    Tie-breakers (FR-9): ``fitScore`` DESC, ``price`` ASC,
    ``furnitureId`` ASC. Guarantees byte-identical output for identical
    inputs (AC-3).
    """
    buckets: dict[str, List[ScoredItem]] = {k: [] for k in CATEGORY_KEYS}
    for item in catalog:
        if item.type not in buckets:
            # Unknown type — silently dropped. The four PRD categories are
            # the only consumers (FR-3 / AC-4).
            continue
        scored = score_item(
            item,
            resolved_style=resolved_style,
            room=room,
            room_color_hex=room_color_hex,
            detected_objects=detected_objects,
        )
        if scored is not None:
            buckets[item.type].append(scored)

    # Sort & truncate. Python's sorted is stable, so we get deterministic
    # ordering as long as the multi-key comparator covers every tier.
    for cat in CATEGORY_KEYS:
        buckets[cat].sort(
            key=lambda s: (-s.fitScore, s.price, s.furnitureId)
        )
        if top_n_per_category > 0:
            buckets[cat] = buckets[cat][:top_n_per_category]

    return buckets


# ---------------------------------------------------------------------------
# internal helpers
# ---------------------------------------------------------------------------


def _hex_to_rgb01(hex_str: str) -> Tuple[float, float, float]:
    """Parse ``#RRGGBB`` into three floats in ``[0, 1]``.

    Raises :class:`ValueError` on malformed input. The Pydantic schema
    (``app.schemas_reco``) already enforces the regex, so this is a
    defensive guard for direct unit tests of the scorer.
    """
    s = hex_str.strip().lstrip("#")
    if len(s) != 6:
        raise ValueError(f"Not a 6-char RGB hex: {hex_str!r}")
    try:
        r = int(s[0:2], 16)
        g = int(s[2:4], 16)
        b = int(s[4:6], 16)
    except ValueError as e:
        raise ValueError(f"Not a hex string: {hex_str!r}") from e
    return r / 255.0, g / 255.0, b / 255.0
