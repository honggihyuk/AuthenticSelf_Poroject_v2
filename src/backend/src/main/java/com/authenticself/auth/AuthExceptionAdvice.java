package com.authenticself.auth;

import com.authenticself.controller.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Translates {@link AuthException} into the project-standard {@link ErrorResponse}
 * envelope so frontends can branch on {@code errorCode} the same way they do
 * for the upload / wishlist surfaces. Scoped to the auth controller package so
 * it does not steal exceptions from the existing UploadExceptionAdvice catch-all.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(0)
public class AuthExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionAdvice.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex) {
        String corr = UUID.randomUUID().toString();
        AuthErrorCode code = ex.code();
        log.warn("auth {} code={} correlationId={}",
                code.httpStatus().value(), code, corr);
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), corr));
    }
}
