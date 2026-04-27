package com.authenticself.ai.dto;

/**
 * Request body sent by {@code SpaceAnalysisClient} to the Python
 * {@code POST /analyze/space} endpoint (FR-3).
 * <p>
 * Field names match the Python Pydantic schema verbatim (camelCase) so the
 * default Jackson mapping stays boring.
 */
public record SpaceAnalysisRequest(
        String roomId,
        String photoUrl
) {
}
