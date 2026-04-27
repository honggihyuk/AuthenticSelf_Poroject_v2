package com.authenticself.admin.dto;

import java.util.Map;

/**
 * Wishlist-tile response payload (UC-03-admin-overview FR-7 / AC-20 / AC-21).
 * <p>
 * Keys:
 * <ul>
 *   <li>{@code totalItems} — COUNT(*) on {@code wishlist} within window
 *       (filter on {@code added_at}).</li>
 *   <li>{@code statusDistribution} — map keyed by exactly
 *       {@code {ACTIVE, PURCHASED}} (REST-layer UPPER_SNAKE; translated
 *       from DB casing {@code Active} / {@code Purchased} — UC-02-wishlist
 *       convention). Sum == {@code totalItems}.</li>
 *   <li>{@code categoryDistribution} — map keyed by exactly
 *       {@code {desk, bed, chair, lighting}} (zero-count buckets present).
 *       Sum == {@code totalItems}.</li>
 *   <li>{@code conversionRate} — {@code PURCHASED / totalItems} rounded to
 *       2 dp. {@code 0.0} when {@code totalItems == 0} (no divide-by-zero,
 *       AC-21).</li>
 * </ul>
 */
public record WishlistTile(
        long totalItems,
        Map<String, Long> statusDistribution,
        Map<String, Long> categoryDistribution,
        double conversionRate
) {
}
