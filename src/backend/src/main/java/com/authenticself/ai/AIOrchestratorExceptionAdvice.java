package com.authenticself.ai;

import com.authenticself.controller.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Translates AI-pipeline exceptions into the shared {@code ErrorResponse}
 * envelope (FR-15 / AC-18 / AC-19 / AC-20).
 * <p>
 * Ordered {@link Ordered#HIGHEST_PRECEDENCE} so these handlers win over the
 * {@link com.authenticself.controller.UploadExceptionAdvice} catch-all for
 * {@link Exception} — otherwise the upload advice would intercept
 * {@link AIException} and return {@code STORAGE_PERSIST_FAILED}.
 */
@RestControllerAdvice(basePackages = "com.authenticself.ai")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AIOrchestratorExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestratorExceptionAdvice.class);

    @ExceptionHandler(AIException.class)
    public ResponseEntity<ErrorResponse> handleAi(AIException ex) {
        String corr = UUID.randomUUID().toString();
        AIErrorCode code = ex.code();
        HttpStatus status = code.httpStatus();

        if (status.is5xxServerError() || status == HttpStatus.BAD_GATEWAY) {
            log.error("ai 5xx code={} correlationId={}", code, corr, ex);
        } else {
            log.warn("ai 4xx code={} correlationId={} msg={}", code, corr, ex.getMessage());
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code.name(), safeMessage(ex), corr));
    }

    @ExceptionHandler(SpaceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SpaceNotFoundException ex) {
        String corr = UUID.randomUUID().toString();
        AIErrorCode code = AIErrorCode.SPACE_NOT_FOUND;
        log.warn("ai 404 code={} correlationId={} msg={}", code, corr, ex.getMessage());
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    private static String safeMessage(AIException ex) {
        String msg = ex.getMessage();
        return (msg == null || msg.isBlank()) ? ex.code().defaultMessage() : msg;
    }
}
