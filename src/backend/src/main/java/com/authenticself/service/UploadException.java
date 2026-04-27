package com.authenticself.service;

/**
 * Domain exception carrying a {@link UploadErrorCode}.
 * <p>
 * Thrown by {@link PhotoUploadService} whenever a validation or persistence
 * concern fails. The {@code @RestControllerAdvice} translates it into the
 * structured {@code ErrorResponse} envelope from FR-6.
 */
public class UploadException extends RuntimeException {

    private final UploadErrorCode code;

    public UploadException(UploadErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public UploadException(UploadErrorCode code, Throwable cause) {
        super(code.defaultMessage(), cause);
        this.code = code;
    }

    public UploadErrorCode code() { return code; }
}
