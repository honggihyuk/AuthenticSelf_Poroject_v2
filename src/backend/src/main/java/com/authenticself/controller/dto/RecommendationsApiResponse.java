package com.authenticself.controller.dto;

import com.authenticself.ai.dto.RecommendationResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public response body for {@code GET /api/v1/spaces/{roomId}/recommendations}
 * (UC-01-recommendation FR-19).
 * <p>
 * Wraps the Python-side {@link RecommendationResponse} and adds two
 * Spring-only fields the Python process does not know about:
 * <ul>
 *   <li>{@code preferredStyle} — the user's chosen style (echoed from
 *       {@code spaces.preferred_style}).</li>
 *   <li>{@code cacheHit} — whether the in-memory cache served this payload
 *       (FR-14 / AC-29).</li>
 * </ul>
 *
 * <p>{@code warning} is {@code null} when no warning applies (FR-12 note).
 * Jackson serialises nulls so the RN client sees a stable shape
 * (see {@code JsonInclude.Include.ALWAYS} default).
 */
public record RecommendationsApiResponse(
        String roomId,
        String status,
        String resolvedStyle,
        String preferredStyle,
        String generatedAt,
        boolean cacheHit,
        RecommendationResponse.Recommendations recommendations,

        // Intentionally not Include.NON_NULL — the contract explicitly
        // says `warning` is either the string or null (FR-19 body shape).
        @JsonInclude(JsonInclude.Include.ALWAYS) String warning,

        Integer processingMs
) {

    /** Build from the orchestrator result. */
    public static RecommendationsApiResponse from(
            RecommendationResponse inner,
            String preferredStyle,
            boolean cacheHit
    ) {
        return new RecommendationsApiResponse(
                inner.roomId(),
                inner.status(),
                inner.resolvedStyle(),
                preferredStyle,
                inner.generatedAt(),
                cacheHit,
                inner.recommendations(),
                inner.warning(),
                inner.processingMs()
        );
    }
}
