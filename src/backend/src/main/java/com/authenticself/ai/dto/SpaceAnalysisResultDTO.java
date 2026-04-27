package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Controller-facing DTO returned by
 * {@link com.authenticself.ai.AIOrchestratorController} on success
 * (both happy-path and short-circuit responses).
 * <p>
 * Distinct from {@link SpaceAnalysisResponse} — the client-facing shape uses
 * the DB-row's flattened {@code "WxLxHm"} dimensions string rather than the
 * nested metric object, matching FR-13.
 * <p>
 * {@code @JsonInclude(NON_NULL)}: the happy-path JSON has no
 * {@code skipped}/{@code reason} fields; the short-circuit JSON has no
 * {@code dimensions}/{@code mainColor}/{@code processingMs}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpaceAnalysisResultDTO(
        String  roomId,
        String  status,
        String  dimensions,    // e.g. "3.6x4.2x2.4m" — null when skipped before analysis ran
        String  mainColor,
        Integer processingMs,  // null when skipped
        Boolean skipped,       // null on happy path, true on NOT_PENDING short-circuit
        String  reason         // "NOT_PENDING" when skipped
) {

    /** Happy path factory — full analysis just ran. */
    public static SpaceAnalysisResultDTO ofAnalyzed(
            String roomId, String dimensions, String mainColor, int processingMs) {
        return new SpaceAnalysisResultDTO(
                roomId, "ANALYZED", dimensions, mainColor, processingMs, null, null);
    }

    /** Short-circuit factory — FR-12 step 2. */
    public static SpaceAnalysisResultDTO ofSkipped(String roomId, String currentStatus) {
        return new SpaceAnalysisResultDTO(
                roomId, currentStatus, null, null, null, Boolean.TRUE, "NOT_PENDING");
    }
}
