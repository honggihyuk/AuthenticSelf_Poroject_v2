package com.authenticself.auth;

/** Domain exception for the {@code /api/v1/auth} surface. */
public class AuthException extends RuntimeException {
    private final AuthErrorCode code;

    public AuthException(AuthErrorCode code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public AuthErrorCode code() { return code; }
}
