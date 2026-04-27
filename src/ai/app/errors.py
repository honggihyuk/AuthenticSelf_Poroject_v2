"""Error codes shared across the Python AI service (FR-7).

Every non-2xx JSON body returned by this service is the envelope
``{errorCode, message, roomId?}``. This module centralises the codes so the
FastAPI exception handlers and the analyzer modules agree on them verbatim.
"""

from __future__ import annotations


# ---------------------------------------------------------------------------
# Error-code string constants (UPPER_SNAKE). The Spring SpaceAnalysisClient
# maps these 1:1 to its own AIErrorCode enum (FR-15).
# ---------------------------------------------------------------------------
INVALID_REQUEST: str = "INVALID_REQUEST"
IMAGE_NOT_FOUND: str = "IMAGE_NOT_FOUND"
IMAGE_READ_FAILED: str = "IMAGE_READ_FAILED"
ANALYSIS_FAILED: str = "ANALYSIS_FAILED"
INTERNAL_ERROR: str = "INTERNAL_ERROR"

# UC-01-recommendation (Task 5) additions.
CATALOG_EMPTY: str = "CATALOG_EMPTY"
RECOMMENDATION_FAILED: str = "RECOMMENDATION_FAILED"


class AnalyzerError(Exception):
    """Base class for analyzer-raised errors that must be translated to HTTP 422.

    Each subclass carries a stable ``code`` attribute matching one of the
    module-level constants above. The FastAPI ``exception_handler`` in
    ``app.main`` converts these to the structured error envelope.
    """

    code: str = ANALYSIS_FAILED

    def __init__(self, message: str) -> None:
        super().__init__(message)
        self.message = message


class ImageNotFoundError(AnalyzerError):
    """Resolved filesystem path does not exist (or lies outside AI_PHOTO_ROOT)."""

    code = IMAGE_NOT_FOUND


class ImageReadError(AnalyzerError):
    """File exists but cv2/Pillow cannot decode it as an image."""

    code = IMAGE_READ_FAILED


class AnalysisError(AnalyzerError):
    """One of the analyzers threw during its ``analyze(...)`` call."""

    code = ANALYSIS_FAILED


# ---------------------------------------------------------------------------
# UC-01-recommendation (Task 5) — new exception classes.
# These are also subclasses of AnalyzerError so the module-level 422 handler
# in app.main renders the standard envelope without extra plumbing.
# ---------------------------------------------------------------------------


class CatalogEmptyError(AnalyzerError):
    """Request arrived with ``catalog == []`` — nothing to rank (FR-12)."""

    code = CATALOG_EMPTY


class RecommendationFailedError(AnalyzerError):
    """Scorer raised unexpectedly; translated to 422 (FR-12)."""

    code = RECOMMENDATION_FAILED
