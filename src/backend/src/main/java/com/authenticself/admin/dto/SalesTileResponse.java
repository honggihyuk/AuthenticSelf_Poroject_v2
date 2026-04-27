package com.authenticself.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standalone wire response for {@code GET /api/v1/admin/sales}.
 * <p>
 * Flattens the {@link SalesTile} fields under the {@code window} /
 * {@code generatedAt} envelope (AC-53). Body shape matches FR-8.
 */
public record SalesTileResponse(
        String         window,
        OffsetDateTime generatedAt,
        long           totalSalesKrw,
        long           purchasedItemCount,
        long           averageOrderKrw,
        Map<String, Long> salesByCategory,
        List<DateKrw>  salesByDay
) {

    public static SalesTileResponse of(String window, OffsetDateTime at, SalesTile t) {
        return new SalesTileResponse(
                window, at,
                t.totalSalesKrw(),
                t.purchasedItemCount(),
                t.averageOrderKrw(),
                t.salesByCategory(),
                t.salesByDay()
        );
    }
}
