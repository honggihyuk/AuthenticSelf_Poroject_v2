"""Protocol and common result type for space analyzers (FR-4, AC-11).

The name ``base.py`` is load-bearing: AC-11 requires the ``SpaceAnalyzer``
Protocol to live in this exact filename. Concrete analyzers live in
sibling modules (``color.py``, ``dimensions.py``, ``style.py``).
"""

from __future__ import annotations

from pathlib import Path
from typing import Protocol, runtime_checkable

from pydantic import BaseModel, ConfigDict, Field


class SpaceAnalysisResult(BaseModel):
    """Per-analyzer output shape.

    ``confidence`` is the analyzer's own self-reported confidence in [0,1].
    The aggregate response confidence (FR-9) is computed in ``app.main`` by
    averaging ``DimensionsEstimator.confidence`` and
    ``ColorExtractor.confidence``; the style classifier's value is not
    mixed into the space-analysis response.
    """

    model_config = ConfigDict(extra="forbid")

    # The dimensions analyzer populates these; color-only / style-only
    # analyzers leave them at their defaults and rely on the orchestrator
    # to ignore the unused fields.
    widthM: float = Field(default=0.0, ge=0.0)
    lengthM: float = Field(default=0.0, ge=0.0)
    heightM: float = Field(default=0.0, ge=0.0)

    mainColor: str = Field(default="#000000", pattern=r"^#[0-9A-F]{6}$")
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)


@runtime_checkable
class SpaceAnalyzer(Protocol):
    """Pluggable analyzer contract.

    Implementations MUST be stateless across calls (or guarantee
    thread-safety) because the FastAPI endpoint runs them inside
    ``asyncio.to_thread`` and they may be invoked concurrently.
    """

    def analyze(self, image_path: Path) -> SpaceAnalysisResult:
        """Inspect the image at ``image_path`` and return a result.

        Raises:
            app.errors.ImageNotFoundError: if the path does not exist.
            app.errors.ImageReadError: if the bytes cannot be decoded.
            app.errors.AnalysisError: on any other analyzer failure.
        """
        ...
