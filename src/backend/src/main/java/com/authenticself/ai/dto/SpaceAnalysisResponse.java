package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Success body returned by the Python {@code POST /analyze/space} service.
 * <p>
 * Nested record {@link Dimensions} mirrors the Python Pydantic model.
 * {@code @JsonIgnoreProperties(ignoreUnknown=true)} so the Python side can
 * add fields without breaking older Spring builds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpaceAnalysisResponse(
        String roomId,
        String status,
        Dimensions dimensions,
        String mainColor,
        Double confidence,
        Integer processingMs
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dimensions(
            Double widthM,
            Double lengthM,
            Double heightM,
            Double areaM2
    ) {
    }
}
