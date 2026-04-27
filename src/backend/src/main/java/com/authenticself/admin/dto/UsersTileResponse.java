package com.authenticself.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Standalone wire response for {@code GET /api/v1/admin/users}.
 * <p>
 * Top-level {@code window} and {@code generatedAt} precede the tile-
 * specific payload (AC-53), and the tile fields are flattened as
 * top-level record components (not via {@code @JsonUnwrapped}) so the
 * body shape matches the FR-5 spec exactly — not a nested
 * {@code users: {...}} object, which is the shape used by
 * {@link AdminOverviewResponse}.
 *
 * <p>The byte-for-byte field set here mirrors the inlined {@link UsersTile}
 * payload under {@code users} in the master overview response; the
 * static factory {@link #of(String, OffsetDateTime, UsersTile)} is the
 * mechanism that satisfies the AC-33 byte-identical invariant across the
 * two endpoint shapes.
 */
public record UsersTileResponse(
        String         window,
        OffsetDateTime generatedAt,
        long           totalUsers,
        long           totalAdmins,
        long           newSignups,
        long           activeUsers,
        List<DateCount> signupsByDay
) {

    public static UsersTileResponse of(String window, OffsetDateTime at, UsersTile t) {
        return new UsersTileResponse(
                window, at,
                t.totalUsers(),
                t.totalAdmins(),
                t.newSignups(),
                t.activeUsers(),
                t.signupsByDay()
        );
    }
}
