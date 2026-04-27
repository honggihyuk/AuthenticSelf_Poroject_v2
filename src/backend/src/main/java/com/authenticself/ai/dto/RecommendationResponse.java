package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response body returned by the Python {@code POST /recommend/furniture}
 * service AND (with one extra field) by the Spring public endpoint
 * {@code GET /api/v1/spaces/{roomId}/recommendations}
 * (UC-01-recommendation FR-2 / FR-19).
 *
 * <p>Spring's public endpoint adds {@code preferredStyle} and
 * {@code cacheHit} to the wire shape — the {@link Recommendations} and
 * other nested types below stay identical between the two hops.
 *
 * @param warning either {@code "NO_FIT_ANY_CATEGORY"} or {@code null}.
 *                See FR-12 note: this is NOT an HTTP error, just a
 *                flag for the RN client to render the dedicated UX.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendationResponse(
        String roomId,
        String status,
        String resolvedStyle,
        String generatedAt,
        Recommendations recommendations,
        String warning,
        Integer processingMs
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendations(
            List<RecommendationItem> desk,
            List<RecommendationItem> bed,
            List<RecommendationItem> chair,
            List<RecommendationItem> lighting
    ) { }
}
