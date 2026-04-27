package com.authenticself.admin.dto;

import java.util.List;
import java.util.Map;

/**
 * Sales-tile response payload (UC-03-admin-overview FR-8 / AC-22..AC-25).
 * <p>
 * Keys:
 * <ul>
 *   <li>{@code totalSalesKrw}       — SUM({@code wishlist.price}) over
 *       rows with status='Purchased' AND purchased_at within window.
 *       Uses the snapshot price (AC-23), NOT current {@code furniture.price}.</li>
 *   <li>{@code purchasedItemCount}  — COUNT(*) of the same filter.</li>
 *   <li>{@code averageOrderKrw}     — {@code round(totalSalesKrw / purchasedItemCount)};
 *       {@code 0} when count is 0.</li>
 *   <li>{@code salesByCategory}     — map keyed by every
 *       {@code {desk, bed, chair, lighting}}; zero buckets present; sum
 *       == {@code totalSalesKrw}.</li>
 *   <li>{@code salesByDay}          — dense ascending series; zero-krw
 *       days included.</li>
 * </ul>
 */
public record SalesTile(
        long totalSalesKrw,
        long purchasedItemCount,
        long averageOrderKrw,
        Map<String, Long> salesByCategory,
        List<DateKrw> salesByDay
) {
}
