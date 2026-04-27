package com.authenticself.wishlist;

import org.springframework.http.HttpStatus;

/**
 * Error codes for the public {@code /api/v1/wishlist/*} endpoints
 * (UC-02-wishlist FR-9 / AC-29).
 * <p>
 * Shape mirrors {@link com.authenticself.space.SpaceErrorCode}: every
 * value carries an {@link HttpStatus} and a Korean default message.
 * {@link com.authenticself.wishlist.WishlistExceptionAdvice} emits
 * {@code new ErrorResponse(code.name(), message, correlationId)} on the
 * shared envelope.
 *
 * <p>{@code MISSING_USER_HEADER} is included here verbatim so the
 * {@link com.authenticself.wishlist.WishlistExceptionAdvice} can
 * translate the framework-level {@code MissingRequestHeaderException}
 * without reaching across packages. The string value matches the one
 * emitted by {@link com.authenticself.space.SpaceErrorCode#MISSING_USER_HEADER}
 * so the RN client's shared error mapping does not care which package
 * produced the 400 (AC-14 / UC-01-recommendation convention preserved).
 */
public enum WishlistErrorCode {

    INVALID_WISHLIST_PAYLOAD        (HttpStatus.BAD_REQUEST, "위시리스트 요청 값이 올바르지 않습니다."),
    INVALID_WISHLIST_STATUS_FILTER  (HttpStatus.BAD_REQUEST, "위시리스트 상태 필터 값이 올바르지 않습니다. (ACTIVE 또는 PURCHASED)"),
    INVALID_STATE_TRANSITION        (HttpStatus.BAD_REQUEST, "지원하지 않는 상태 전이입니다."),
    MISSING_USER_HEADER             (HttpStatus.BAD_REQUEST, "사용자 인증 정보가 없습니다."),

    USER_NOT_FOUND                  (HttpStatus.NOT_FOUND,   "사용자를 찾을 수 없습니다."),
    FURNITURE_NOT_FOUND             (HttpStatus.NOT_FOUND,   "요청하신 가구 정보를 찾을 수 없습니다."),
    WISHLIST_ITEM_NOT_FOUND         (HttpStatus.NOT_FOUND,   "위시리스트 항목을 찾을 수 없습니다."),

    WISHLIST_LIMIT_EXCEEDED         (HttpStatus.CONFLICT,    "위시리스트에 저장할 수 있는 항목 수를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String     defaultMessage;

    WishlistErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus()     { return httpStatus; }
    public String     defaultMessage() { return defaultMessage; }
}
