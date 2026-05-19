package com.authenticself.auth.dto;

/** Body of {@code POST /api/v1/auth/login}. */
public record LoginRequest(String username, String password) {
}
