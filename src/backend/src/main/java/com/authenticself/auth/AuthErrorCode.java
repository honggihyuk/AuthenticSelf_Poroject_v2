package com.authenticself.auth;

import org.springframework.http.HttpStatus;

/** Stable wire codes for the {@code /api/v1/auth} surface and the
 *  {@link JwtAuthFilter} (UC-SECURE-AUTH FR-12 / AC-16). */
public enum AuthErrorCode {
    MISSING_FIELDS      (HttpStatus.BAD_REQUEST,  "아이디와 비밀번호를 입력해주세요."),
    INVALID_CREDENTIALS (HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    // UC-SECURE-AUTH FR-12 — emitted by JwtService.verify failures AND
    // by JwtAuthFilter when the Authorization / X-User-Id header is
    // absent or malformed on a non-login /api/v1/** request.
    INVALID_TOKEN       (HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다."),
    // UC-SECURE-AUTH FR-12 — emitted by JwtAuthFilter when X-User-Id is
    // present but does not match the verified JWT sub claim.
    USER_ID_MISMATCH    (HttpStatus.UNAUTHORIZED, "사용자 식별자가 일치하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus     = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus()     { return httpStatus; }
    public String     defaultMessage() { return defaultMessage; }
}
