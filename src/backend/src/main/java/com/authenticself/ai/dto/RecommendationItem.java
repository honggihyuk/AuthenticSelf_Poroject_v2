package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One ranked furniture item returned by the Python service + forwarded
 * to the RN client (UC-01-recommendation FR-2).
 * <p>
 * Field names mirror the Pydantic schema on the Python side verbatim so
 * Jackson's default camelCase mapping just works.
 *
 * @param type one of {@code desk}, {@code bed}, {@code chair}, {@code lighting}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendationItem(
        String furnitureId,
        String name,
        String type,
        Integer price,
        String imageUrl,
        /** Phase A — curated 3D model URL for AR placement; nullable.
         *  Filled in by Spring from {@code furniture.model_url} after Python
         *  returns (Python schema is intentionally unchanged). */
        String modelUrl,
        Double fitScore,
        ScoreBreakdown scoreBreakdown,
        String rationale
) {
    /** Return a copy with {@code modelUrl} replaced. */
    public RecommendationItem withModelUrl(String newModelUrl) {
        return new RecommendationItem(
                furnitureId, name, type, price, imageUrl, newModelUrl,
                fitScore, scoreBreakdown, rationale
        );
    }
}
