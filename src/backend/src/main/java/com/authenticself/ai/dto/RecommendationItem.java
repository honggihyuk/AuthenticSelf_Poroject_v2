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
        Double fitScore,
        ScoreBreakdown scoreBreakdown,
        String rationale
) {
}
