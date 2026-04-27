package com.authenticself.admin;

/**
 * Three-value time-window enum for the admin aggregation endpoints
 * (UC-03-admin-overview FR-4 / FR-10 / D-4 / AC-10 / AC-11).
 *
 * <p>Values are the canonical UPPER_SNAKE wire form used on the REST
 * surface {@code ?window=LAST_7D|LAST_30D|ALL}. Unknown values raise 400
 * {@code INVALID_TIME_WINDOW} via {@link #parse(String)} which the
 * {@link AdminController} invokes before routing into the service layer.
 *
 * <p>Default is {@link #ALL} — absent query parameter resolves to ALL
 * (AC-10). Parsing is case-insensitive so {@code last_30d}, {@code Last_30D}
 * and {@code LAST_30D} all succeed (AC-11).
 */
public enum TimeWindow {
    LAST_7D,
    LAST_30D,
    ALL;

    /**
     * Parse a (case-insensitive) query-string value into a
     * {@link TimeWindow}. Returns {@link #ALL} for {@code null} / blank
     * (default per AC-10). Any other non-matching value raises
     * {@link AdminException} with {@link AdminErrorCode#INVALID_TIME_WINDOW}
     * — 400 per FR-11 / AC-11.
     */
    public static TimeWindow parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        String normalized = raw.trim().toUpperCase();
        for (TimeWindow w : values()) {
            if (w.name().equals(normalized)) {
                return w;
            }
        }
        throw new AdminException(AdminErrorCode.INVALID_TIME_WINDOW);
    }
}
