package com.authenticself.controller.dto;

import com.authenticself.space.PreferredStyle;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Response body for the public endpoints
 * {@code GET /api/v1/spaces/{roomId}} and
 * {@code PUT /api/v1/spaces/{roomId}/preferred-style} (Task-4 FR-14 / AC-19).
 * <p>
 * All analysis fields are nullable so the same record can represent every
 * lifecycle phase:
 * <ul>
 *   <li>{@code PENDING_ANALYSIS} — only {@code roomId}, {@code status},
 *       {@code uploadedAt} are populated.</li>
 *   <li>{@code ANALYZED} — all fields populated except optionally
 *       {@code style} / {@code styleConfidence} / {@code preferredStyle}.</li>
 *   <li>{@code FAILED} — {@code status} plus {@code uploadedAt} and
 *       {@code analysisDate}.</li>
 * </ul>
 *
 * {@code @JsonInclude(NON_ABSENT)} keeps null fields present in the JSON
 * as {@code null} (AC-19 asserts {@code preferredStyle==null} literally).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpaceResponse(
        String         roomId,
        String         status,
        String         dimensions,
        String         mainColor,
        String         style,
        Double         styleConfidence,
        PreferredStyle preferredStyle,
        LocalDateTime  analysisDate,
        LocalDateTime  uploadedAt
) {
}
