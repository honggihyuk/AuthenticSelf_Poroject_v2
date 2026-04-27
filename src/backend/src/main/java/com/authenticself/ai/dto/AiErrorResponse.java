package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Python-side error envelope (FR-7). Parsed by
 * {@link com.authenticself.ai.SpaceAnalysisClient} when the Python service
 * responds non-2xx, then translated via
 * {@link com.authenticself.ai.AIErrorCode#fromPythonCode(String)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiErrorResponse(
        String errorCode,
        String message,
        String roomId
) {
}
