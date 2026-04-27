package com.authenticself.admin;

import com.authenticself.controller.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Translates {@link AdminException} and a few framework exceptions into
 * the shared {@code ErrorResponse} envelope for the admin REST surface
 * (UC-03-admin-overview FR-11 / AC-29 / AC-47).
 * <p>
 * Scoped to {@code com.authenticself.admin} so it does not shadow the
 * sibling {@code SpaceExceptionAdvice} / {@code WishlistExceptionAdvice}
 * handlers — identical pattern.
 * {@link Ordered#HIGHEST_PRECEDENCE} so these handlers win over any
 * catch-all advice elsewhere in the process.
 *
 * <p>Every emitted response body is the three-field shape
 * {@code (errorCode, message, correlationId)} — byte-identical to the
 * envelope emitted by the sibling advisers (AC-47). No new envelope shape
 * is introduced by this task.
 */
@RestControllerAdvice(basePackages = "com.authenticself.admin")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(AdminExceptionAdvice.class);

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<ErrorResponse> handleAdmin(AdminException ex) {
        String corr = UUID.randomUUID().toString();
        AdminErrorCode code = ex.code();
        HttpStatus status = code.httpStatus();

        if (status.is5xxServerError()) {
            log.error("admin 5xx code={} correlationId={}", code, corr, ex);
        } else {
            log.warn("admin 4xx code={} correlationId={}", code, corr);
        }
        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? code.defaultMessage() : ex.getMessage();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code.name(), msg, corr));
    }

    /**
     * Defensive handler for the framework-level
     * {@link MissingRequestHeaderException} — raised only if a future
     * handler method forgets the in-method {@code X-User-Id} check that
     * {@link AdminAuthorizer#requireAdmin} performs as its first step.
     * Translates to 400 {@code MISSING_USER_HEADER} — same shape as the
     * wishlist / space packages.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String corr = UUID.randomUUID().toString();
        AdminErrorCode code = AdminErrorCode.MISSING_USER_HEADER;
        log.warn("admin 4xx code={} correlationId={} header={}",
                code, corr, ex.getHeaderName());
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }
}
