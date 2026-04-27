package com.authenticself.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link WindowResolver} + {@link TimeWindow#parse}
 * (UC-03-admin-overview AC-11 / AC-32).
 * <p>
 * Fixed-clock scenarios: "now" is pinned at 2026-04-18T00:00:00+09:00
 * so every LAST_* boundary is day-aligned and exact.
 */
class WindowResolverTest {

    private static final ZoneId KST   = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = LocalDate.of(2026, 4, 18)
            .atStartOfDay(KST).toInstant();

    private WindowResolver resolver() {
        Clock fixed = Clock.fixed(NOW, KST);
        return new WindowResolver(fixed, "Asia/Seoul");
    }

    // -----------------------------------------------------------------
    // AC-32 — fixed-clock window math.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-32: LAST_7D windowStart = 2026-04-11T00:00:00+09:00")
    void last7d_ac32() {
        Instant start = resolver().resolve(TimeWindow.LAST_7D);
        assertThat(start).isEqualTo(
                LocalDate.of(2026, 4, 11).atStartOfDay(KST).toInstant());
    }

    @Test
    @DisplayName("AC-32: LAST_30D windowStart = 2026-03-19T00:00:00+09:00")
    void last30d_ac32() {
        Instant start = resolver().resolve(TimeWindow.LAST_30D);
        assertThat(start).isEqualTo(
                LocalDate.of(2026, 3, 19).atStartOfDay(KST).toInstant());
    }

    @Test
    @DisplayName("AC-32 / D-4: ALL windowStart is null (no predicate)")
    void all_ac32() {
        assertThat(resolver().resolve(TimeWindow.ALL)).isNull();
    }

    // -----------------------------------------------------------------
    // AC-11 — window parsing behaviour.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-10 / AC-11: null or blank raw → ALL (default)")
    void parseDefault_ac10() {
        assertThat(TimeWindow.parse(null)).isEqualTo(TimeWindow.ALL);
        assertThat(TimeWindow.parse("")).isEqualTo(TimeWindow.ALL);
        assertThat(TimeWindow.parse("   ")).isEqualTo(TimeWindow.ALL);
    }

    @Test
    @DisplayName("AC-11: window parse is case-insensitive")
    void parseCaseInsensitive_ac11() {
        assertThat(TimeWindow.parse("LAST_30D")).isEqualTo(TimeWindow.LAST_30D);
        assertThat(TimeWindow.parse("last_30d")).isEqualTo(TimeWindow.LAST_30D);
        assertThat(TimeWindow.parse("Last_30D")).isEqualTo(TimeWindow.LAST_30D);
        assertThat(TimeWindow.parse("all")).isEqualTo(TimeWindow.ALL);
        assertThat(TimeWindow.parse("LAST_7D")).isEqualTo(TimeWindow.LAST_7D);
    }

    @Test
    @DisplayName("AC-11: unknown window value → INVALID_TIME_WINDOW")
    void parseInvalid_ac11() {
        assertThatThrownBy(() -> TimeWindow.parse("YESTERDAY"))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.INVALID_TIME_WINDOW));
        assertThatThrownBy(() -> TimeWindow.parse("LAST_90D"))
                .isInstanceOfSatisfying(AdminException.class, ex ->
                        assertThat(ex.code()).isEqualTo(AdminErrorCode.INVALID_TIME_WINDOW));
    }
}
