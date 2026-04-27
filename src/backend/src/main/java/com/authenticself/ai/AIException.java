package com.authenticself.ai;

/**
 * Unchecked domain exception carrying an {@link AIErrorCode} (FR-11, FR-15).
 * <p>
 * The {@link AIOrchestratorExceptionAdvice} translates it to the shared
 * {@code ErrorResponse} envelope. Subclassing is intentional — callers often
 * distinguish transport failures from analyzer failures (e.g. the
 * {@link AIOrchestrator} leaves the DB row at {@code PENDING_ANALYSIS} on
 * transport failures and flips to {@code FAILED} on analyzer failures).
 */
public class AIException extends RuntimeException {

    private final AIErrorCode code;

    public AIException(AIErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public AIException(AIErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AIException(AIErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AIErrorCode code() { return code; }

    /** Convenience: did this exception originate from the HTTP/transport layer? */
    public boolean isTransport() {
        return code == AIErrorCode.AI_SERVICE_UNAVAILABLE;
    }
}
