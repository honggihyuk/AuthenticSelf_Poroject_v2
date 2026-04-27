package com.authenticself.space;

/**
 * Unchecked domain exception carrying a {@link SpaceErrorCode} (Task-4 FR-14).
 * <p>
 * Translated to the shared {@code ErrorResponse} envelope by
 * {@link SpaceExceptionAdvice}.
 */
public class SpaceException extends RuntimeException {

    private final SpaceErrorCode code;

    public SpaceException(SpaceErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public SpaceException(SpaceErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public SpaceErrorCode code() { return code; }
}
