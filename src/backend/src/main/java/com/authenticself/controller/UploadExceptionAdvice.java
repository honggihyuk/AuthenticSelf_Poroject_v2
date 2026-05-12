package com.authenticself.controller;

import com.authenticself.controller.dto.ErrorResponse;
import com.authenticself.service.UploadErrorCode;
import com.authenticself.service.UploadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

/**
 * Translates {@link UploadException} and a small set of framework exceptions
 * into the structured {@code ErrorResponse} envelope mandated by FR-6.
 * <p>
 * All uncaught exceptions get mapped to {@code STORAGE_PERSIST_FAILED} with a
 * correlation id — see {@link #handleUncaught}.
 */
@RestControllerAdvice
public class UploadExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(UploadExceptionAdvice.class);

    @ExceptionHandler(UploadException.class)
    public ResponseEntity<ErrorResponse> handleUpload(UploadException ex) {
        String corr = UUID.randomUUID().toString();
        UploadErrorCode code = ex.code();
        HttpStatus status = code.httpStatus();

        if (status.is5xxServerError()) {
            log.error("upload 5xx code={} correlationId={}", code, corr, ex);
        } else {
            log.warn("upload 4xx code={} correlationId={} msg={}", code, corr, ex.getMessage());
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    /** Spring-raised before our controller ever runs when the multipart exceeds limits. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        String corr = UUID.randomUUID().toString();
        log.warn("upload 4xx code=FILE_TOO_LARGE correlationId={}", corr);
        UploadErrorCode code = UploadErrorCode.FILE_TOO_LARGE;
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    /** Thrown by Spring when {@code @RequestHeader(required=true)} is absent. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String corr = UUID.randomUUID().toString();
        UploadErrorCode code = "X-User-Id".equalsIgnoreCase(ex.getHeaderName())
                ? UploadErrorCode.MISSING_USER_HEADER
                : UploadErrorCode.STORAGE_PERSIST_FAILED;
        log.warn("upload 4xx code={} correlationId={} header={}",
                code, corr, ex.getHeaderName());
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    /**
     * Static-resource miss (Spring 3.2+) — collapse to a clean 404 so frontends
     * can distinguish "asset not found" from a real server fault. Before this
     * handler existed, the catch-all below mapped every NoResourceFoundException
     * to a STORAGE_PERSIST_FAILED 500, which made debugging Phase A asset
     * pipeline issues (missing GLB / wrong path) painful.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        String corr = UUID.randomUUID().toString();
        log.info("static 404 path={} correlationId={}", ex.getResourcePath(), corr);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "리소스를 찾을 수 없습니다.", corr));
    }

    /** Last-resort catch-all — assigns STORAGE_PERSIST_FAILED per NFR Error-handling. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncaught(Exception ex) {
        String corr = UUID.randomUUID().toString();
        log.error("upload 5xx code=STORAGE_PERSIST_FAILED correlationId={}", corr, ex);
        UploadErrorCode code = UploadErrorCode.STORAGE_PERSIST_FAILED;
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }
}
