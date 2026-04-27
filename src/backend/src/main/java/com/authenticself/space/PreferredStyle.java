package com.authenticself.space;

/**
 * Canonical user-choice style enum (FR-8 / AC-10).
 * <p>
 * Superset of {@link Style} by exactly one value: {@code CURRENT}
 * ("현재 디자인 그대로"), which means "don't change the style — recommend
 * furniture consistent with the analysed room."
 *
 * <p>Persisted as a string in {@code spaces.preferred_style} via
 * {@code @Enumerated(EnumType.STRING)} and serialised to the JSON
 * body of {@code GET /api/v1/spaces/{roomId}} and
 * {@code PUT /api/v1/spaces/{roomId}/preferred-style} as its UPPERCASE
 * name (Jackson default).
 */
public enum PreferredStyle {
    CURRENT,
    MODERN,
    SIMPLE,
    CLASSIC,
    SCANDINAVIAN,
    INDUSTRIAL;

    /**
     * Case-sensitive parser used by the controller when validating the
     * PUT body. Returns {@code null} on unknown inputs so the caller can
     * emit a 400 {@code INVALID_PREFERRED_STYLE} instead of a 500.
     */
    public static PreferredStyle fromNullable(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (PreferredStyle s : values()) {
            if (s.name().equals(raw)) return s;
        }
        return null;
    }
}
