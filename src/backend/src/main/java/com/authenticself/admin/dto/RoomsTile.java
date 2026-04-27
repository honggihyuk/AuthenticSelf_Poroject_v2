package com.authenticself.admin.dto;

import com.authenticself.domain.Space;
import com.authenticself.space.Style;

import java.util.List;
import java.util.Map;

/**
 * Rooms-tile response payload (UC-03-admin-overview FR-6 / AC-16..AC-19).
 * <p>
 * Keys:
 * <ul>
 *   <li>{@code totalSpaces}       — COUNT(*) on {@code spaces} within window.</li>
 *   <li>{@code statusDistribution} — map keyed by every
 *       {@link Space.Status} value (zero-count buckets present). Sum ==
 *       {@code totalSpaces} (AC-18 invariant).</li>
 *   <li>{@code styleDistribution}  — map keyed by every {@link Style}
 *       value (zero-count buckets present). Sum &le; {@code totalSpaces};
 *       ANALYZED rows with NULL style contribute to the total but to no
 *       style bucket (AC-17 / AC-35).</li>
 *   <li>{@code mainColorTop5}     — top-N colors by count, tie-broken by
 *       color ASC. N bounded by {@code app.admin.top-colors-limit}.</li>
 * </ul>
 */
public record RoomsTile(
        long totalSpaces,
        Map<Space.Status, Long> statusDistribution,
        Map<Style, Long>        styleDistribution,
        List<ColorCount>        mainColorTop5
) {
}
