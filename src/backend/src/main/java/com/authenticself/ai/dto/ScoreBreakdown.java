package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Per-item sub-score breakdown (UC-01-recommendation FR-2).
 * <p>
 * Key names are fixed: exactly {@code sizeFit}, {@code styleMatch},
 * {@code colorHarmony}, {@code objectConflict}. Each value is in
 * {@code [0.0, 1.0]} rounded to 2 dp.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreBreakdown(
        Double sizeFit,
        Double styleMatch,
        Double colorHarmony,
        Double objectConflict
) {
}
