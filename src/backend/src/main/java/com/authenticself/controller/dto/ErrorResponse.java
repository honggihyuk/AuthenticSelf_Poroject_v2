package com.authenticself.controller.dto;

/**
 * Standard error envelope for every non-2xx response (FR-6).
 * <p>
 * {@code errorCode} is machine-matchable (stable English UPPER_SNAKE),
 * {@code message} is user-facing Korean, {@code correlationId} is a UUIDv4
 * so the client and the backend log can be stitched together.
 */
public record ErrorResponse(
        String errorCode,
        String message,
        String correlationId
) {
}
