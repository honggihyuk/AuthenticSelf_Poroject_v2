package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Success body of AI {@code POST /analyze/objects}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ObjectsAnalysisResponse(
        String roomId,
        String status,
        int imageWidth,
        int imageHeight,
        List<DetectedObject> objects,
        int processingMs
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetectedObject(
            String label,
            List<Double> bbox,
            double confidence
    ) {}
}
