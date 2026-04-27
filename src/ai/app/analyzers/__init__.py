"""Analyzer package (FR-4).

Exports a lightweight factory :func:`default_analyzers` that returns the
three concrete analyzers. Tests can substitute fakes by constructing
their own tuple and passing it to the route handler via ``Depends``.

``StyleClassifier`` lives here for Task-4 reference but is NOT wired into
``/analyze/space`` — it exists so the next task can import it without
refactoring (FR-10).
"""

from __future__ import annotations

from dataclasses import dataclass

from .base import SpaceAnalysisResult, SpaceAnalyzer
from .color import ColorExtractor
from .dimensions import DimensionsEstimator
from .style import STYLE_LABELS, StyleAnalysisResult, StyleClassifier


@dataclass(frozen=True)
class AnalyzerBundle:
    """Bundle of analyzers wired into the ``/analyze/space`` endpoint.

    Only ``dimensions`` and ``color`` participate in the FR-9 aggregate
    confidence. ``style`` is retained here for Task-4 discoverability.
    """

    dimensions: DimensionsEstimator
    color: ColorExtractor
    style: StyleClassifier


def default_analyzers() -> AnalyzerBundle:
    """Return a fresh bundle of analyzers (NFR: no shared mutable state)."""
    return AnalyzerBundle(
        dimensions=DimensionsEstimator(),
        color=ColorExtractor(),
        style=StyleClassifier(),
    )


def default_style_classifier() -> StyleClassifier:
    """Return a fresh :class:`StyleClassifier` for ``POST /analyze/style``.

    Used by the FastAPI ``Depends`` injection surface — analogous to
    :func:`default_analyzers` but only returns the style classifier,
    because ``/analyze/style`` has no need for the dimensions/colour
    estimators.
    """
    return StyleClassifier()


__all__ = [
    "AnalyzerBundle",
    "ColorExtractor",
    "DimensionsEstimator",
    "SpaceAnalysisResult",
    "SpaceAnalyzer",
    "STYLE_LABELS",
    "StyleAnalysisResult",
    "StyleClassifier",
    "default_analyzers",
    "default_style_classifier",
]
