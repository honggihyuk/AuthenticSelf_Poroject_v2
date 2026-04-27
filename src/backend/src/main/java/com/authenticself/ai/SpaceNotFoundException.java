package com.authenticself.ai;

/**
 * Thrown by {@link AIOrchestrator} when the requested roomId has no row in
 * {@code spaces}. Mapped to HTTP 404 with errorCode {@code SPACE_NOT_FOUND}
 * by {@link AIOrchestratorExceptionAdvice} (FR-15 / AC-20).
 */
public class SpaceNotFoundException extends RuntimeException {
    public SpaceNotFoundException(String roomId) {
        super("No spaces row for roomId=" + roomId);
    }
}
