package com.authenticself.admin.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Standalone wire response for {@code GET /api/v1/admin/wishlist}.
 * <p>
 * Flattens the {@link WishlistTile} fields under the {@code window} /
 * {@code generatedAt} envelope (AC-53). Body shape matches FR-7.
 */
public record WishlistTileResponse(
        String         window,
        OffsetDateTime generatedAt,
        long           totalItems,
        Map<String, Long> statusDistribution,
        Map<String, Long> categoryDistribution,
        double         conversionRate
) {

    public static WishlistTileResponse of(String window, OffsetDateTime at, WishlistTile t) {
        return new WishlistTileResponse(
                window, at,
                t.totalItems(),
                t.statusDistribution(),
                t.categoryDistribution(),
                t.conversionRate()
        );
    }
}
