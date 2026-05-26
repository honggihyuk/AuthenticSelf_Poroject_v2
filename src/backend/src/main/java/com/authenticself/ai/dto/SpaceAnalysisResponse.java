package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Success body returned by the Python {@code POST /analyze/space} service.
 * <p>
 * Nested record {@link Dimensions} mirrors the Python Pydantic model.
 * {@code @JsonIgnoreProperties(ignoreUnknown=true)} so the Python side can
 * add fields without breaking older Spring builds.
 *
 * <p>UC-ML-PERSIST FR-6 — three additive fields deserialize the YOLO output
 * the AI service now surfaces: {@code detections} (label / absolute-px bbox /
 * confidence), plus {@code imageWidth} / {@code imageHeight} so the backend
 * can persist a self-describing envelope and the recommender transform can
 * normalize bboxes later. All three are nullable: an older AI deploy that
 * omits them leaves them {@code null} (the {@code @JsonIgnoreProperties}
 * contract keeps mixed-version deploys safe).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpaceAnalysisResponse(
        String roomId,
        String status,
        Dimensions dimensions,
        String mainColor,
        Double confidence,
        Integer processingMs,
        Integer imageWidth,
        Integer imageHeight,
        List<Detection> detections
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dimensions(
            Double widthM,
            Double lengthM,
            Double heightM,
            Double areaM2
    ) {
    }

    /**
     * One YOLO detection, mirroring the Python {@code DetectedObject} shape
     * verbatim: {@code label}, {@code bbox} = [x1,y1,x2,y2] absolute pixels
     * (xyxy), {@code confidence} in [0,1].
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detection(
            String label,
            List<Double> bbox,
            Double confidence
    ) {
    }
}
