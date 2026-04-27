package com.authenticself.ai.dto;

/**
 * Request body sent by {@link com.authenticself.ai.StyleAnalysisClient} to
 * the Python {@code POST /analyze/style} endpoint (Task-4 FR-2 / FR-8).
 * <p>
 * Field names are identical to {@link SpaceAnalysisRequest} so the Python
 * Pydantic side can share validation rules — the default Jackson mapping
 * stays boring.
 */
public record StyleAnalysisRequest(
        String roomId,
        String photoUrl
) {
}
