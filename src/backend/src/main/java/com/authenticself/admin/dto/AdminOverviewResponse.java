package com.authenticself.admin.dto;

import java.time.OffsetDateTime;

/**
 * Master endpoint response for {@code GET /api/v1/admin/overview}
 * (UC-03-admin-overview FR-4 / AC-9 / AC-33 / AC-53).
 * <p>
 * The per-tile sub-objects inside this payload MUST be byte-identical to
 * the corresponding {@code /api/v1/admin/{tile}} endpoints called under
 * the same {@code window} (AC-33). Both payloads are built from the same
 * service-layer DTOs so this invariant holds by construction.
 *
 * <p>{@code generatedAt} is an {@link OffsetDateTime} serialised by Jackson
 * as ISO-8601 with {@code +09:00} offset (Asia/Seoul, configurable via
 * {@code app.admin.time-zone}).
 */
public record AdminOverviewResponse(
        String         window,
        OffsetDateTime generatedAt,
        UsersTile      users,
        RoomsTile      rooms,
        WishlistTile   wishlist,
        SalesTile      sales
) {
}
