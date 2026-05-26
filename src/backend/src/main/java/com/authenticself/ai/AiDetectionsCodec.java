package com.authenticself.ai;

import com.authenticself.ai.dto.AiDetectionsEnvelope;
import com.authenticself.ai.dto.RecommendationRequest;
import com.authenticself.ai.dto.SpaceAnalysisResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UC-ML-PERSIST FR-7 / FR-9 / FR-13 — serialize/deserialize the persisted
 * {@code spaces.ai_detections} envelope and transform persisted detections
 * into the recommender DTO.
 *
 * <p>This is a pure, stateless utility (static methods + a private shared
 * {@link ObjectMapper}) so the transform is unit-testable without a Spring
 * context (AC-9, AC-13).
 *
 * <h3>Transform contract (FR-9 / data-contract §5)</h3>
 * <ul>
 *   <li><b>Coordinate normalization</b>: {@code bboxNorm = [x1/W, y1/H,
 *       x2/W, y2/H]}, each clamped to [0,1].</li>
 *   <li><b>Label → type mapping</b>: COCO labels that match a catalog type
 *       (currently {@code chair}, {@code bed}) pass through to that type so
 *       the Python {@code score_object_conflict} can fire. COCO labels with
 *       no catalog equivalent ({@code tv}, {@code couch}, {@code potted
 *       plant}, …) pass through with their raw label as {@code type} —
 *       harmless because they never equal a catalog {@code item.type}.
 *       {@code desk} / {@code lighting} have NO COCO class, so nothing maps
 *       to them — an accepted, documented gap (NOT fixed with new ML).</li>
 * </ul>
 *
 * <h3>Degrade-safely (FR-13 / AC-13)</h3>
 * Malformed / legacy JSON in {@code ai_detections} (or a {@code null} column)
 * yields an empty list and logs a warning, rather than failing the caller.
 */
public final class AiDetectionsCodec {

    private static final Logger log = LoggerFactory.getLogger(AiDetectionsCodec.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * COCO/YOLO label → catalog furniture {@code type} mapping. The catalog
     * types are {@code {desk, bed, chair, lighting}}; only {@code chair} and
     * {@code bed} have a direct COCO class. The map is DATA, not a model:
     * extend it here if the catalog/COCO overlap grows. Labels absent from
     * this map pass through unchanged (they simply never equal a catalog
     * {@code item.type}).
     */
    static final Map<String, String> LABEL_TO_TYPE = Map.of(
            "chair", "chair",
            "bed", "bed"
    );

    private AiDetectionsCodec() {
    }

    // -----------------------------------------------------------------
    // FR-7 — serialize the AI response detections into the persisted envelope.
    // -----------------------------------------------------------------

    /**
     * Build the {@code spaces.ai_detections} JSON envelope from a successful
     * {@code /analyze/space} response. Returns {@code null} when the response
     * carries no usable image dimensions (so the column stays NULL rather than
     * persisting an un-normalizable envelope). An empty detection list with
     * valid dimensions serializes to {@code {"imageWidth":..,"detections":[]}}.
     */
    public static String toEnvelopeJson(SpaceAnalysisResponse response) {
        if (response == null
                || response.imageWidth() == null
                || response.imageHeight() == null
                || response.imageWidth() <= 0
                || response.imageHeight() <= 0) {
            return null;
        }

        List<SpaceAnalysisResponse.Detection> src =
                response.detections() != null ? response.detections() : List.of();

        List<AiDetectionsEnvelope.Detection> dets = new ArrayList<>(src.size());
        for (SpaceAnalysisResponse.Detection d : src) {
            dets.add(new AiDetectionsEnvelope.Detection(d.label(), d.bbox(), d.confidence()));
        }

        AiDetectionsEnvelope envelope = new AiDetectionsEnvelope(
                response.imageWidth(), response.imageHeight(), dets);
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            // Serialization of a typed object should never fail; log + skip so
            // the analysis happy path is never blocked by detection persistence.
            log.warn("ai_detections serialization failed roomId={} exc={}",
                    response.roomId(), e.toString());
            return null;
        }
    }

    // -----------------------------------------------------------------
    // FR-9 / FR-13 — transform persisted envelope into recommender DTOs.
    // -----------------------------------------------------------------

    /**
     * Parse a persisted {@code ai_detections} JSON envelope and transform each
     * detection into a {@link RecommendationRequest.DetectedObject}
     * ({@code type}, 4-element {@code bboxNorm} in [0,1], {@code confidence}).
     * <p>
     * Returns an empty (never null) list for: a {@code null}/blank column,
     * malformed JSON, missing/zero image dimensions, or a malformed bbox —
     * logging a warning so the recommendation request proceeds (FR-13 / AC-13).
     */
    public static List<RecommendationRequest.DetectedObject> toDetectedObjects(String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return List.of();
        }

        AiDetectionsEnvelope envelope;
        try {
            envelope = MAPPER.readValue(envelopeJson, AiDetectionsEnvelope.class);
        } catch (Exception e) {
            log.warn("ai_detections malformed JSON — degrading to empty list: {}", e.toString());
            return List.of();
        }

        if (envelope == null
                || envelope.imageWidth() == null
                || envelope.imageHeight() == null
                || envelope.imageWidth() <= 0
                || envelope.imageHeight() <= 0
                || envelope.detections() == null) {
            log.warn("ai_detections envelope missing dimensions/detections — degrading to empty list");
            return List.of();
        }

        double w = envelope.imageWidth();
        double h = envelope.imageHeight();

        List<RecommendationRequest.DetectedObject> out =
                new ArrayList<>(envelope.detections().size());
        for (AiDetectionsEnvelope.Detection d : envelope.detections()) {
            List<Double> bbox = d.bbox();
            if (bbox == null || bbox.size() != 4 || d.label() == null) {
                log.warn("ai_detections skipping malformed detection label={} bbox={}",
                        d.label(), bbox);
                continue;
            }
            List<Double> bboxNorm = List.of(
                    clamp01(bbox.get(0) / w),
                    clamp01(bbox.get(1) / h),
                    clamp01(bbox.get(2) / w),
                    clamp01(bbox.get(3) / h)
            );
            String type = LABEL_TO_TYPE.getOrDefault(d.label(), d.label());
            Double confidence = d.confidence() != null ? d.confidence() : 0.0;
            out.add(new RecommendationRequest.DetectedObject(type, bboxNorm, confidence));
        }
        return out;
    }

    /**
     * Parse a persisted {@code ai_detections} JSON envelope into the typed
     * {@link AiDetectionsEnvelope}, or {@code null} for a NULL/blank column or
     * malformed JSON (degrade-safely — FR-11/FR-12 admin overlay treats null
     * as "no detections" and renders the empty-state hint without crashing).
     */
    public static AiDetectionsEnvelope parseEnvelope(String envelopeJson) {
        if (envelopeJson == null || envelopeJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(envelopeJson, AiDetectionsEnvelope.class);
        } catch (Exception e) {
            log.warn("ai_detections envelope parse failed — returning null: {}", e.toString());
            return null;
        }
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
