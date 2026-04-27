package com.authenticself.space;

/**
 * Canonical AI-output style enum (FR-8 / AC-10).
 * <p>
 * This enum is intentionally DIFFERENT from {@link PreferredStyle}: the
 * Python style classifier only ever emits one of the five values here
 * ({@code CURRENT} is a user-only choice and belongs to
 * {@link PreferredStyle} exclusively).
 *
 * <p>String values match the Python {@code StyleLabel} literal verbatim
 * and are used as the wire format for the style field across HTTP and
 * as the DB string persisted into {@code spaces.style}.
 */
public enum Style {
    MODERN,
    SIMPLE,
    CLASSIC,
    SCANDINAVIAN,
    INDUSTRIAL;

    /**
     * Defensive parser used when mapping Python response strings into a
     * typed enum. Returns {@code null} for unknown values rather than
     * throwing — the orchestrator treats a null style as "style analysis
     * didn't produce a usable value" and falls back to partial-success
     * behaviour (FR-12).
     */
    public static Style fromNullable(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (Style s : values()) {
            if (s.name().equals(raw)) return s;
        }
        return null;
    }
}
