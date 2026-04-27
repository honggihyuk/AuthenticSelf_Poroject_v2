package com.authenticself.wishlist;

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
 * Translates {@link WishlistException} and a few framework exceptions
 * into the shared {@code ErrorResponse} envelope for the wishlist REST
 * surface (UC-02-wishlist FR-9 / AC-30).
 * <p>
 * Scoped to {@code com.authenticself.wishlist} so it does not shadow the
 * sibling {@code SpaceExceptionAdvice} / {@code UploadExceptionAdvice}
 * handlers — identical pattern to the space advice.
 * {@link Ordered#HIGHEST_PRECEDENCE} so these handlers win over any
 * catch-all advice elsewhere in the process.
 */
@RestControllerAdvice(basePackages = "com.authenticself.wishlist")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WishlistExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(WishlistExceptionAdvice.class);

    @ExceptionHandler(WishlistException.class)
    public ResponseEntity<ErrorResponse> handleWishlist(WishlistException ex) {
        String corr = UUID.randomUUID().toString();
        WishlistErrorCode code = ex.code();
        HttpStatus status = code.httpStatus();

        if (status.is5xxServerError()) {
            log.error("wishlist 5xx code={} correlationId={}", code, corr, ex);
        } else {
            log.warn("wishlist 4xx code={} correlationId={} msg={}",
                    code, corr, ex.getMessage());
        }
        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? code.defaultMessage() : ex.getMessage();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code.name(), msg, corr));
    }

    /**
     * AC-14 — a missing {@code X-User-Id} header should surface as 400
     * {@code MISSING_USER_HEADER}. Spring raises
     * {@link MissingRequestHeaderException} automatically when a required
     * header is absent, but the controller declares the header as
     * {@code required=false} + short-circuits to {@link WishlistException}
     * so this handler is defensive for any future handler method that
     * forgets the in-method check.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String corr = UUID.randomUUID().toString();
        WishlistErrorCode code = WishlistErrorCode.MISSING_USER_HEADER;
        log.warn("wishlist 4xx code={} correlationId={} header={}",
                code, corr, ex.getHeaderName());
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }

    /**
     * A non-JSON body (or a shape Jackson cannot parse — e.g. numeric
     * literal where a string is expected) is mapped to 400
     * {@code INVALID_WISHLIST_PAYLOAD} on POST / PATCH. Same stance as
     * {@code SpaceExceptionAdvice}'s handling for that exception.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String corr = UUID.randomUUID().toString();
        WishlistErrorCode code = WishlistErrorCode.INVALID_WISHLIST_PAYLOAD;
        log.warn("wishlist 4xx code={} correlationId={} msg={}",
                code, corr, ex.getMessage());
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }
}
