package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Self-describing envelope persisted into {@code spaces.ai_detections}
 * (UC-ML-PERSIST FR-7, data-contract §5).
 * <p>
 * Storing {@code imageWidth} / {@code imageHeight} alongside the detections
 * makes bbox normalization (FR-9) reproducible without re-reading the image:
 * the recommender transform divides each absolute-pixel bbox coordinate by the
 * persisted dimensions to produce {@code bboxNorm} in [0,1].
 * <p>
 * The JSON shape is:
 * <pre>
 * { "imageWidth": 1280, "imageHeight": 960,
 *   "detections": [ { "label": "chair", "bbox": [120.0,340.5,410.2,880.0], "confidence": 0.91 } ] }
 * </pre>
 * {@code @JsonIgnoreProperties(ignoreUnknown=true)} so legacy/extended JSON
 * round-trips without a hard failure (FR-13 / AC-13 degrade-safely policy).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiDetectionsEnvelope(
        Integer imageWidth,
        Integer imageHeight,
        List<Detection> detections
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detection(
            String label,
            List<Double> bbox,
            Double confidence
    ) {
    }
}
