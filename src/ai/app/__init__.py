"""AuthenticSelf AI service package (UC-01-space-analysis).

Entry-point for uvicorn: ``uvicorn app.main:app --port 8001`` run from
``src/ai/``. The package is intentionally thin; real work lives in
``app.analyzers.*``.
"""

__version__ = "0.1.0"
