package com.authenticself.admin.dto;

import com.authenticself.domain.Space;
import com.authenticself.space.Style;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standalone wire response for {@code GET /api/v1/admin/rooms}.
 * <p>
 * Flattens the {@link RoomsTile} fields under the {@code window} /
 * {@code generatedAt} envelope (AC-53). Body shape matches FR-6.
 */
public record RoomsTileResponse(
        String         window,
        OffsetDateTime generatedAt,
        long           totalSpaces,
        Map<Space.Status, Long> statusDistribution,
        Map<Style, Long>        styleDistribution,
        List<ColorCount>        mainColorTop5
) {

    public static RoomsTileResponse of(String window, OffsetDateTime at, RoomsTile t) {
        return new RoomsTileResponse(
                window, at,
                t.totalSpaces(),
                t.statusDistribution(),
                t.styleDistribution(),
                t.mainColorTop5()
        );
    }
}
