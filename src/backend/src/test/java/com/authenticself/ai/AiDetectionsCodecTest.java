package com.authenticself.ai;

import com.authenticself.ai.dto.RecommendationRequest;
import com.authenticself.ai.dto.SpaceAnalysisResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link AiDetectionsCodec} — the FR-7 serializer and the
 * FR-9 transform (label→type mapping + bbox normalization), plus the FR-13
 * degrade-safely policy. Pure unit tests; no Spring, no DB.
 */
class AiDetectionsCodecTest {

    // -----------------------------------------------------------------
    // FR-7 — serialize the AI response into the persisted envelope
    // -----------------------------------------------------------------

    @Test
    @DisplayName("FR-7: toEnvelopeJson serializes dims + detections")
    void toEnvelopeJson_serializes() {
        SpaceAnalysisResponse resp = response(1280, 960, List.of(
                new SpaceAnalysisResponse.Detection("chair", List.of(128.0, 96.0, 256.0, 192.0), 0.9)));

        String json = AiDetectionsCodec.toEnvelopeJson(resp);

        assertThat(json).contains("\"imageWidth\":1280");
        assertThat(json).contains("\"imageHeight\":960");
        assertThat(json).contains("\"label\":\"chair\"");
    }

    @Test
    @DisplayName("FR-7: empty detection list with valid dims serializes to detections:[]")
    void toEnvelopeJson_emptyDetections() {
        SpaceAnalysisResponse resp = response(1280, 960, List.of());
        String json = AiDetectionsCodec.toEnvelopeJson(resp);
        assertThat(json).contains("\"detections\":[]");
    }

    @Test
    @DisplayName("FR-7: missing image dims → null envelope (column stays NULL)")
    void toEnvelopeJson_missingDimsNull() {
        SpaceAnalysisResponse resp = new SpaceAnalysisResponse(
                "r1", "OK",
                new SpaceAnalysisResponse.Dimensions(3.6, 4.2, 2.4, 15.12),
                "#E8D9B0", 0.78, 142,
                null, null, List.of());
        assertThat(AiDetectionsCodec.toEnvelopeJson(resp)).isNull();
    }

    // -----------------------------------------------------------------
    // FR-9 — transform (round-trip via toEnvelopeJson → toDetectedObjects)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("AC-9: chair bbox[128,96,256,192]@1280x960 → bboxNorm[0.1,0.1,0.2,0.2]")
    void transform_normalizesAndMapsChair() {
        String envelope = AiDetectionsCodec.toEnvelopeJson(response(1280, 960, List.of(
                new SpaceAnalysisResponse.Detection("chair", List.of(128.0, 96.0, 256.0, 192.0), 0.9))));

        List<RecommendationRequest.DetectedObject> out =
                AiDetectionsCodec.toDetectedObjects(envelope);

        assertThat(out).hasSize(1);
        var d = out.get(0);
        assertThat(d.type()).isEqualTo("chair");
        assertThat(d.bboxNorm().get(0)).isCloseTo(0.1, within(1e-3));
        assertThat(d.bboxNorm().get(1)).isCloseTo(0.1, within(1e-3));
        assertThat(d.bboxNorm().get(2)).isCloseTo(0.2, within(1e-3));
        assertThat(d.bboxNorm().get(3)).isCloseTo(0.2, within(1e-3));
        assertThat(d.confidence()).isCloseTo(0.9, within(1e-3));
    }

    @Test
    @DisplayName("FR-9: bed maps to bed; non-catalog labels (tv/couch) pass through raw")
    void transform_labelMapping() {
        String envelope = AiDetectionsCodec.toEnvelopeJson(response(1000, 1000, List.of(
                new SpaceAnalysisResponse.Detection("bed", List.of(0.0, 0.0, 500.0, 500.0), 0.8),
                new SpaceAnalysisResponse.Detection("tv", List.of(0.0, 0.0, 100.0, 100.0), 0.6),
                new SpaceAnalysisResponse.Detection("couch", List.of(0.0, 0.0, 100.0, 100.0), 0.6))));

        List<RecommendationRequest.DetectedObject> out =
                AiDetectionsCodec.toDetectedObjects(envelope);

        assertThat(out).extracting(RecommendationRequest.DetectedObject::type)
                .containsExactly("bed", "tv", "couch");
        // Documented gap: no COCO class maps to desk/lighting, so neither
        // appears here. This is intentional (out of scope), not a bug.
        assertThat(out).extracting(RecommendationRequest.DetectedObject::type)
                .doesNotContain("desk", "lighting");
    }

    @Test
    @DisplayName("FR-9: out-of-frame bbox coords clamp into [0,1]")
    void transform_clampsOutOfRange() {
        // bbox larger than the image → x2/W and y2/H > 1 must clamp to 1.0.
        String envelope = AiDetectionsCodec.toEnvelopeJson(response(100, 100, List.of(
                new SpaceAnalysisResponse.Detection("chair", List.of(-10.0, -10.0, 200.0, 200.0), 0.7))));

        var d = AiDetectionsCodec.toDetectedObjects(envelope).get(0);
        assertThat(d.bboxNorm()).allSatisfy(v -> assertThat(v).isBetween(0.0, 1.0));
        assertThat(d.bboxNorm().get(0)).isEqualTo(0.0);
        assertThat(d.bboxNorm().get(2)).isEqualTo(1.0);
    }

    // -----------------------------------------------------------------
    // FR-13 — degrade safely
    // -----------------------------------------------------------------

    @Test
    @DisplayName("AC-12: null/blank ai_detections → empty list")
    void transform_nullDegradesToEmpty() {
        assertThat(AiDetectionsCodec.toDetectedObjects(null)).isEmpty();
        assertThat(AiDetectionsCodec.toDetectedObjects("")).isEmpty();
        assertThat(AiDetectionsCodec.toDetectedObjects("   ")).isEmpty();
    }

    @Test
    @DisplayName("AC-13: malformed JSON → empty list (no exception)")
    void transform_malformedDegradesToEmpty() {
        assertThat(AiDetectionsCodec.toDetectedObjects("{ not valid ]]")).isEmpty();
        assertThat(AiDetectionsCodec.toDetectedObjects("[1,2,3]")).isEmpty();
    }

    @Test
    @DisplayName("FR-13: envelope missing dims → empty list")
    void transform_envelopeMissingDims() {
        // Legacy envelope without imageWidth/Height cannot be normalized.
        String legacy = "{\"detections\":[{\"label\":\"chair\",\"bbox\":[1,2,3,4],\"confidence\":0.9}]}";
        assertThat(AiDetectionsCodec.toDetectedObjects(legacy)).isEmpty();
    }

    @Test
    @DisplayName("FR-13: detection with wrong-arity bbox is skipped, others survive")
    void transform_skipsMalformedDetection() {
        String envelope = "{\"imageWidth\":100,\"imageHeight\":100,\"detections\":["
                + "{\"label\":\"chair\",\"bbox\":[1,2,3],\"confidence\":0.9},"
                + "{\"label\":\"bed\",\"bbox\":[0,0,50,50],\"confidence\":0.8}]}";
        var out = AiDetectionsCodec.toDetectedObjects(envelope);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo("bed");
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static SpaceAnalysisResponse response(
            int w, int h, List<SpaceAnalysisResponse.Detection> dets) {
        return new SpaceAnalysisResponse(
                "r1", "OK",
                new SpaceAnalysisResponse.Dimensions(3.6, 4.2, 2.4, 15.12),
                "#E8D9B0", 0.78, 142,
                w, h, dets);
    }
}
