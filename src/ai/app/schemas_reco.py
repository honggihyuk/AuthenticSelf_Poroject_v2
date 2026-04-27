"""Pydantic v2 models for ``POST /recommend/furniture`` (FR-2).

Kept in a dedicated module (rather than appended to ``app.schemas``) so
the upstream task's contracts remain immediately greppable.
"""

from __future__ import annotations

from typing import Dict, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field


# ---- enums ---------------------------------------------------------------

# Same five AI-output labels as app.schemas.StyleLabel plus null.
DetectedStyle = Literal[
    "MODERN",
    "SIMPLE",
    "CLASSIC",
    "SCANDINAVIAN",
    "INDUSTRIAL",
]

# User-choice superset (FR-2, FR-4).
PreferredStyle = Literal[
    "CURRENT",
    "MODERN",
    "SIMPLE",
    "CLASSIC",
    "SCANDINAVIAN",
    "INDUSTRIAL",
]

FurnitureType = Literal["desk", "bed", "chair", "lighting"]


# ---- nested input types --------------------------------------------------


class RecoDimensions(BaseModel):
    model_config = ConfigDict(extra="forbid")
    widthM: float = Field(..., gt=0.0)
    lengthM: float = Field(..., gt=0.0)
    heightM: float = Field(..., gt=0.0)
    areaM2: Optional[float] = Field(default=None, ge=0.0)


class DetectedObjectModel(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: str = Field(..., min_length=1, max_length=64)
    bboxNorm: List[float] = Field(..., min_length=4, max_length=4)
    confidence: float = Field(..., ge=0.0, le=1.0)


class SpaceBlock(BaseModel):
    model_config = ConfigDict(extra="forbid")
    dimensions: RecoDimensions
    mainColor: str = Field(..., pattern=r"^#[0-9A-Fa-f]{6}$")
    detectedStyle: Optional[DetectedStyle] = None
    detectedObjects: List[DetectedObjectModel] = Field(default_factory=list)


class CatalogItem(BaseModel):
    model_config = ConfigDict(extra="forbid")
    furnitureId: str = Field(..., min_length=1, max_length=64)
    type: FurnitureType
    name: str = Field(..., min_length=1, max_length=200)
    styleTags: List[str] = Field(default_factory=list)
    widthCm: int = Field(..., ge=1)
    lengthCm: int = Field(..., ge=1)
    heightCm: int = Field(..., ge=1)
    colorHex: str = Field(..., pattern=r"^#[0-9A-Fa-f]{6}$")
    price: int = Field(..., ge=0)
    imageUrl: Optional[str] = None


# ---- request / response --------------------------------------------------


class RecommendRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, extra="forbid")

    roomId: str = Field(
        ...,
        min_length=1,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
    )
    userId: str = Field(..., min_length=1, max_length=64)
    space: SpaceBlock
    preferredStyle: PreferredStyle
    catalog: List[CatalogItem] = Field(default_factory=list)
    topNPerCategory: int = Field(default=3, ge=1, le=10)


class ScoreBreakdownModel(BaseModel):
    model_config = ConfigDict(extra="forbid")
    sizeFit: float
    styleMatch: float
    colorHarmony: float
    objectConflict: float


class RecommendationItemModel(BaseModel):
    model_config = ConfigDict(extra="forbid")
    furnitureId: str
    name: str
    type: FurnitureType
    price: int
    imageUrl: Optional[str] = None
    fitScore: float
    scoreBreakdown: ScoreBreakdownModel
    rationale: str


class RecommendationsBlock(BaseModel):
    model_config = ConfigDict(extra="forbid")
    desk: List[RecommendationItemModel] = Field(default_factory=list)
    bed: List[RecommendationItemModel] = Field(default_factory=list)
    chair: List[RecommendationItemModel] = Field(default_factory=list)
    lighting: List[RecommendationItemModel] = Field(default_factory=list)


class RecommendResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    roomId: str
    status: Literal["OK"] = "OK"
    resolvedStyle: DetectedStyle
    generatedAt: str
    recommendations: RecommendationsBlock
    warning: Optional[Literal["NO_FIT_ANY_CATEGORY"]] = None
    processingMs: int = Field(..., ge=0)
