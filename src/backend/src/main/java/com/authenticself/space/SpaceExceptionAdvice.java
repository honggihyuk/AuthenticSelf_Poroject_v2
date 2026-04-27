package com.authenticself.space;

import com.authenticself.ai.AIErrorCode;
import com.authenticself.ai.AIException;
import com.authenticself.controller.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Translates {@link SpaceException} and a few framework exceptions into the
 * shared {@code ErrorResponse} envelope (Task-4 FR-14 / AC-20 / AC-21 /
 * AC-24 / AC-25).
 * <p>
 * {@link Ordered#HIGHEST_PRECEDENCE} so these handlers win over the
 * {@link com.authenticself.controller.UploadExceptionAdvice} catch-all.
 */
@RestControllerAdvice(basePackages = "com.authenticself.space")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaceExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(SpaceExceptionAdvice.class);

    @ExceptionHandler(SpaceException.class)
    public ResponseEntity<ErrorResponse> handleSpace(SpaceException ex) {
        String corr = UUID.randomUUID().toString();
        SpaceErrorCode code = ex.code();
        HttpStatus status = code.httpStatus();

        if (status.is5xxServerError()) {
            log.error("space 5xx code={} correlationId={}", code, corr, ex);
        } else {
            log.warn("space 4xx code={} correlationId={} msg={}", code, corr, ex.getMessage());
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String corr = UUID.randomUUID().toString();
        SpaceErrorCode code = SpaceErrorCode.MISSING_USER_HEADER;
        log.warn("space 4xx code={} correlationId={} header={}",
                code, corr, ex.getHeaderName());
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String corr = UUID.randomUUID().toString();
        SpaceErrorCode code = SpaceErrorCode.INVALID_PREFERRED_STYLE;
        log.warn("space 4xx code={} correlationId={} msg={}", code, corr, ex.getMessage());
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    /**
     * UC-01-recommendation FR-19 — the public
     * {@code GET /api/v1/spaces/{roomId}/recommendations} endpoint lives
     * in this package but delegates to {@link com.authenticself.ai.RecommendationOrchestrator},
     * which raises {@link AIException} on Python/transport failures. The
     * {@link com.authenticself.ai.AIOrchestratorExceptionAdvice} only
     * scopes itself to controllers in {@code com.authenticself.ai}; we
     * therefore handle {@link AIException} here too so the Spring
     * {@link ErrorResponse} envelope is emitted consistently (AC-36 / AC-37).
     */
    @ExceptionHandler(AIException.class)
    public ResponseEntity<ErrorResponse> handleAi(AIException ex) {
        String corr = UUID.randomUUID().toString();
        AIErrorCode code = ex.code();
        HttpStatus status = code.httpStatus();

        if (status.is5xxServerError() || status == HttpStatus.BAD_GATEWAY) {
            log.error("space-ai 5xx code={} correlationId={}", code, corr, ex);
        } else {
            log.warn("space-ai 4xx code={} correlationId={} msg={}",
                    code, corr, ex.getMessage());
        }
        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? code.defaultMessage() : ex.getMessage();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code.name(), msg, corr));
    }
}
