"""Pydantic v2 request/response models for the space-analysis service (FR-3).

These models are the single source of truth for the Python side of the
OpenAPI contract. The Spring ``SpaceAnalysisResultDTO`` mirrors the success
schema field-for-field.
"""

from __future__ import annotations

from typing import Dict, List, Literal, Optional, Tuple

from pydantic import BaseModel, ConfigDict, Field


# Canonical AI-output style labels (FR-2 / FR-8 / AC-2 / AC-4). `CURRENT`
# is a user-only choice and is intentionally NOT part of this literal.
StyleLabel = Literal[
    "MODERN",
    "SIMPLE",
    "CLASSIC",
    "SCANDINAVIAN",
    "INDUSTRIAL",
]


# ---------------------------------------------------------------------------
# Request
# ---------------------------------------------------------------------------


class SpaceAnalyzeRequest(BaseModel):
    """Request body for ``POST /analyze/space``.

    Validation rules (FR-3):
      - ``roomId`` is 1-64 chars, ``^[A-Za-z0-9_-]+$``.
      - ``photoUrl`` starts with ``file://`` (other schemes are out of scope).
    """

    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    roomId: str = Field(
        ...,
        min_length=1,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
        description="Client-supplied ULID/UUID identifying the space row.",
    )
    photoUrl: str = Field(
        ...,
        min_length=8,
        max_length=2048,
        pattern=r"^file://.+",
        description="Absolute file:// URL pointing at the uploaded image on local disk.",
    )


# ---------------------------------------------------------------------------
# Response — success
# ---------------------------------------------------------------------------


class Dimensions(BaseModel):
    """Estimated room dimensions in metres, plus computed area."""

    model_config = ConfigDict(extra="forbid")

    widthM: float = Field(..., ge=0.0)
    lengthM: float = Field(..., ge=0.0)
    heightM: float = Field(..., ge=0.0)
    areaM2: float = Field(..., ge=0.0)


class SpaceAnalysisResponse(BaseModel):
    """Success body (HTTP 200) of ``POST /analyze/space``.

    UC-ML-PERSIST FR-3 — three additive fields surface the YOLO detections the
    dimensions analyzer already computed (no extra inference): ``detections``
    reuses the :class:`DetectedObject` shape verbatim, and ``imageWidth`` /
    ``imageHeight`` let the backend normalize bboxes downstream without
    re-reading the image. Forward references to ``DetectedObject`` are fine —
    it is declared further down in this module and resolved at import time.
    """

    model_config = ConfigDict(extra="forbid")

    roomId: str
    status: str = Field(..., pattern=r"^OK$")
    dimensions: Dimensions
    mainColor: str = Field(..., pattern=r"^#[0-9A-F]{6}$")
    confidence: float = Field(..., ge=0.0, le=1.0)
    processingMs: int = Field(..., ge=0)
    imageWidth: int = Field(..., ge=1)
    imageHeight: int = Field(..., ge=1)
    detections: List["DetectedObject"] = Field(default_factory=list)


# ---------------------------------------------------------------------------
# Style-analysis request / response (FR-2, Task 4)
# ---------------------------------------------------------------------------


class StyleAnalyzeRequest(BaseModel):
    """Request body for ``POST /analyze/style``.

    Validation rules are identical to :class:`SpaceAnalyzeRequest`
    (FR-2): ``roomId`` is 1-64 chars ``^[A-Za-z0-9_-]+$``, ``photoUrl``
    starts with ``file://``.
    """

    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    roomId: str = Field(
        ...,
        min_length=1,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
        description="Client-supplied ULID/UUID identifying the space row.",
    )
    photoUrl: str = Field(
        ...,
        min_length=8,
        max_length=2048,
        pattern=r"^file://.+",
        description="Absolute file:// URL pointing at the uploaded image on local disk.",
    )


class StyleAnalysisResponse(BaseModel):
    """Success body (HTTP 200) of ``POST /analyze/style`` (FR-2).

    Fields:
      - ``style`` — argmax of ``scores``; one of the five AI-output labels.
        ``CURRENT`` is a user-only choice and MUST NEVER appear here (AC-4).
      - ``confidence`` — ``scores[style]`` rounded to 2 dp.
      - ``scores`` — all five labels as keys, sum in ``[0.99, 1.01]``.
    """

    model_config = ConfigDict(extra="forbid")

    roomId: str
    status: str = Field(..., pattern=r"^OK$")
    style: StyleLabel
    confidence: float = Field(..., ge=0.0, le=1.0)
    scores: Dict[StyleLabel, float]
    processingMs: int = Field(..., ge=0)


# ---------------------------------------------------------------------------
# Object-detection (YOLO) request/response — POST /analyze/objects
# ---------------------------------------------------------------------------


class ObjectsAnalyzeRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    roomId: str = Field(
        ..., min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$",
    )
    photoUrl: str = Field(
        ..., min_length=8, max_length=2048, pattern=r"^file://.+",
    )


class DetectedObject(BaseModel):
    model_config = ConfigDict(extra="forbid")

    label: str
    bbox: Tuple[float, float, float, float] = Field(
        ..., description="(x1, y1, x2, y2) absolute pixel coords",
    )
    confidence: float = Field(..., ge=0.0, le=1.0)


class ObjectsAnalysisResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    roomId: str
    status: str = Field(..., pattern=r"^OK$")
    imageWidth: int = Field(..., ge=1)
    imageHeight: int = Field(..., ge=1)
    objects: List[DetectedObject]
    processingMs: int = Field(..., ge=0)


# ---------------------------------------------------------------------------
# Response — error envelope (FR-7)
# ---------------------------------------------------------------------------


class ErrorResponse(BaseModel):
    """Non-2xx envelope. ``roomId`` is echoed when known."""

    model_config = ConfigDict(extra="forbid")

    errorCode: str
    message: str
    roomId: Optional[str] = None


# UC-ML-PERSIST FR-3 — resolve the forward reference in
# ``SpaceAnalysisResponse.detections`` now that ``DetectedObject`` is defined.
SpaceAnalysisResponse.model_rebuild()
