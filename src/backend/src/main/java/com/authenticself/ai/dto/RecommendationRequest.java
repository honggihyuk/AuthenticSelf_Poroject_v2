package com.authenticself.ai.dto;

import java.util.List;

/**
 * Request body sent by {@link com.authenticself.ai.RecommendationClient} to
 * the Python {@code POST /recommend/furniture} endpoint
 * (UC-01-recommendation FR-2).
 * <p>
 * Field names match the Python Pydantic schema verbatim (camelCase) so
 * Jackson's default mapping is boring.
 *
 * @param roomId            opaque room id (same rule as /analyze/space).
 * @param userId            opaque user id (non-empty, &le; 64 chars).
 * @param space             room signal block.
 * @param preferredStyle    one of CURRENT/MODERN/SIMPLE/CLASSIC/SCANDINAVIAN/INDUSTRIAL.
 * @param catalog           eligible catalog items, non-empty.
 * @param topNPerCategory   1..10, default 3.
 */
public record RecommendationRequest(
        String roomId,
        String userId,
        Space space,
        String preferredStyle,
        List<CatalogItem> catalog,
        Integer topNPerCategory
) {

    /** Room signal sent to the Python scorer. */
    public record Space(
            Dimensions dimensions,
            String mainColor,
            String detectedStyle,
            List<DetectedObject> detectedObjects
    ) { }

    public record Dimensions(
            Double widthM,
            Double lengthM,
            Double heightM,
            Double areaM2
    ) { }

    /**
     * Detected object from the YOLO pipeline. For this task (FR-18 step 8)
     * Spring always sends an empty list because the detections are not yet
     * persisted per-row. Keeping the shape here so the Python contract
     * does not shift when the future detection-persistence task lands.
     */
    public record DetectedObject(
            String type,
            List<Double> bboxNorm,
            Double confidence
    ) { }

    /** One furniture-catalog row flattened for the scorer. */
    public record CatalogItem(
            String furnitureId,
            String type,
            String name,
            List<String> styleTags,
            Integer widthCm,
            Integer lengthCm,
            Integer heightCm,
            String colorHex,
            Integer price,
            String imageUrl
    ) { }
}
