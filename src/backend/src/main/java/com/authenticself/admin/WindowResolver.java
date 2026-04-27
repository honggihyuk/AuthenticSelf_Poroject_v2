package com.authenticself.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Resolves {@link TimeWindow} values into concrete {@link Instant}
 * window-start boundaries at {@code Asia/Seoul} midnight alignment
 * (UC-03-admin-overview FR-10 / D-4 / AC-32).
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@link TimeWindow#LAST_7D}  &rarr; {@code now(KST) - 7 days}  at 00:00:00 KST.</li>
 *   <li>{@link TimeWindow#LAST_30D} &rarr; {@code now(KST) - 30 days} at 00:00:00 KST.</li>
 *   <li>{@link TimeWindow#ALL}      &rarr; {@code null} (no predicate).</li>
 * </ul>
 *
 * <p>The midnight alignment guarantees deterministic test fixtures: for a
 * fixed clock at {@code 2026-04-18T00:00:00+09:00}, {@code LAST_7D} equals
 * {@code 2026-04-11T00:00:00+09:00} exactly (AC-32).
 *
 * <p>Repository queries use the {@code (:windowStart IS NULL OR column &gt;= :windowStart)}
 * idiom so the same method body supports both "window applied" and
 * "no window" in a single native SQL string.
 */
@Component
public class WindowResolver {

    private final Clock  clock;
    private final ZoneId zoneId;

    public WindowResolver(
            Clock clock,
            @Value("${app.admin.time-zone:Asia/Seoul}") String zoneIdKey
    ) {
        this.clock  = clock;
        this.zoneId = ZoneId.of(zoneIdKey);
    }

    /**
     * Convert a {@link TimeWindow} into the inclusive lower bound for the
     * filter column, as an {@link Instant} suitable for binding into a
     * native SQL {@code WHERE column &gt;= :windowStart} predicate.
     * <p>
     * Returns {@code null} for {@link TimeWindow#ALL} — callers must
     * propagate the null down to the repository layer so the
     * {@code (:windowStart IS NULL OR ...)} branch wins.
     */
    public Instant resolve(TimeWindow window) {
        if (window == null || window == TimeWindow.ALL) {
            return null;
        }
        ZonedDateTime nowZ = ZonedDateTime.now(clock.withZone(zoneId));
        LocalDate today = nowZ.toLocalDate();
        int daysBack = switch (window) {
            case LAST_7D  -> 7;
            case LAST_30D -> 30;
            default       -> throw new AdminException(AdminErrorCode.INVALID_TIME_WINDOW);
        };
        return today.minusDays(daysBack).atStartOfDay(zoneId).toInstant();
    }

    /**
     * Upper bound for the dense "by day" series — always "today (KST)"
     * inclusive, at the end-of-day boundary 23:59:59.999. Callers use
     * this paired with {@link #resolve} to drive
     * {@code signupsByDay} / {@code salesByDay} range construction.
     */
    public LocalDate today() {
        return ZonedDateTime.now(clock.withZone(zoneId)).toLocalDate();
    }

    /** Zone id used for all window math — exposed for tests. */
    public ZoneId zone() {
        return zoneId;
    }
}
