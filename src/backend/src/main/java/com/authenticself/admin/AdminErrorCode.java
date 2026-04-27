package com.authenticself.admin;

import org.springframework.http.HttpStatus;

/**
 * Error codes for the public {@code /api/v1/admin/*} endpoints
 * (UC-03-admin-overview FR-11 / AC-28).
 * <p>
 * Shape mirrors {@link com.authenticself.wishlist.WishlistErrorCode} and
 * {@link com.authenticself.space.SpaceErrorCode}: every value carries an
 * {@link HttpStatus} and a Korean default message.
 * {@link AdminExceptionAdvice} emits
 * {@code new ErrorResponse(code.name(), message, correlationId)} on the
 * shared envelope — byte-identical to sibling tasks (AC-29 / AC-47).
 *
 * <p>{@code MISSING_USER_HEADER} is duplicated here verbatim so the
 * {@link AdminExceptionAdvice} can translate framework-level
 * {@code MissingRequestHeaderException} without reaching across packages
 * (same pattern as UC-02-wishlist D-5). The string value matches the one
 * emitted by {@link com.authenticself.space.SpaceErrorCode#MISSING_USER_HEADER}
 * and {@link com.authenticself.wishlist.WishlistErrorCode#MISSING_USER_HEADER}
 * so a client's shared error mapping does not care which package produced
 * the 400.
 *
 * <p>HTTP status mapping (FR-11):
 * <ul>
 *   <li>{@link #MISSING_USER_HEADER} &rarr; 400 (reused shared code).</li>
 *   <li>{@link #INVALID_TIME_WINDOW} &rarr; 400 (new code).</li>
 *   <li>{@link #USER_NOT_FOUND}      &rarr; 404 (reused shared code).</li>
 *   <li>{@link #NOT_ADMIN}           &rarr; 403 (new code).</li>
 * </ul>
 */
public enum AdminErrorCode {

    MISSING_USER_HEADER     (HttpStatus.BAD_REQUEST, "사용자 인증 정보가 없습니다."),
    INVALID_TIME_WINDOW     (HttpStatus.BAD_REQUEST, "기간 필터 값이 올바르지 않습니다. (LAST_7D, LAST_30D, ALL)"),

    USER_NOT_FOUND          (HttpStatus.NOT_FOUND,   "사용자를 찾을 수 없습니다."),

    NOT_ADMIN               (HttpStatus.FORBIDDEN,   "관리자 권한이 필요합니다.");

    private final HttpStatus httpStatus;
    private final String     defaultMessage;

    AdminErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus()     { return httpStatus; }
    public String     defaultMessage() { return defaultMessage; }
}
