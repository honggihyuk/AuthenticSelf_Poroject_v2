package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Success body returned by the Python {@code POST /analyze/style} service
 * (Task-4 FR-2 / FR-8).
 * <p>
 * {@code style} is one of {@code MODERN, SIMPLE, CLASSIC, SCANDINAVIAN,
 * INDUSTRIAL}. {@code scores} is a sparse {@code Map<String, Double>}
 * with exactly the five AI-output keys.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown=true)} so the Python side
 * can add fields without breaking older Spring builds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StyleAnalysisResponse(
        String roomId,
        String status,
        String style,
        Double confidence,
        Map<String, Double> scores,
        Integer processingMs
) {
}
