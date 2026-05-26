package com.authenticself.auth;

import java.time.Instant;

/**
 * Immutable result of {@link JwtService#verify(String)}
 * (UC-SECURE-AUTH FR-3).
 *
 * <p>{@code userId} is the JWT {@code sub} claim; {@code role} is one of
 * {@code USER} / {@code ADMIN}; {@code name} is the display name copied
 * verbatim from the {@code users.name} column at issuance time;
 * {@code expiresAt} is the parsed {@code exp} claim as an {@link Instant}.
 */
public record JwtVerification(
        String userId,
        String role,
        String name,
        Instant expiresAt
) {
}
