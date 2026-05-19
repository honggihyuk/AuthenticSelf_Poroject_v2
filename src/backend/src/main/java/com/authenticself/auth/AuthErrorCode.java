package com.authenticself.auth;

import org.springframework.http.HttpStatus;

/** Stable wire codes for the {@code /api/v1/auth} surface. */
public enum AuthErrorCode {
    MISSING_FIELDS      (HttpStatus.BAD_REQUEST,  "아이디와 비밀번호를 입력해주세요."),
    INVALID_CREDENTIALS (HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus     = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus()     { return httpStatus; }
    public String     defaultMessage() { return defaultMessage; }
}
