package com.authenticself.auth.dto;

/**
 * Successful response for {@code POST /api/v1/auth/login}.
 *
 * <p>{@code token} is an opaque base64 stub for the PoC — the frontend
 * MUST NOT decode it. The {@code role} and {@code userId} fields carried
 * alongside are what callers should persist for routing and the
 * {@code X-User-Id} header on subsequent requests.
 */
public record LoginResponse(
        String token,
        String userId,
        String role,
        String name
) {
}
