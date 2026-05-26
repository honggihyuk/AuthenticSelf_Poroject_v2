package com.authenticself.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * HS256 JWT issuer + verifier for the AuthenticSelf login flow
 * (UC-SECURE-AUTH FR-2..FR-5, FR-SEC-3).
 *
 * <p>Replaces the prior base64 stub token in {@link AuthService}. The
 * frontend response envelope ({@code LoginResponse}) is unchanged — only
 * the {@code token} field's bytes shift from {@code Base64(JSON)} to a
 * signed JWT.
 *
 * <p>Secret is sourced from the {@code app.auth.jwt.secret} property,
 * which itself reads the {@code APP_AUTH_JWT_SECRET} environment
 * variable (no default — fail-fast on startup if missing or shorter than
 * 32 bytes; see {@code docs/deployment/env.md}).
 *
 * <p>Two instances constructed with the same secret bytes mutually
 * verify each other's tokens — HMAC is deterministic and the verifier
 * carries no per-process state (FR-5 / AC-5).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** 7 days, in seconds — exact TTL invariant per FR-4 / AC-4. */
    public static final long TTL_SECONDS = 604_800L;

    /** Minimum secret length in UTF-8 bytes — RFC 7518 §3.2 for HS256. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Clock     clock;

    /**
     * Eager bean constructor — throws {@link IllegalStateException} when
     * the secret is null/empty/shorter than 32 bytes so the Spring
     * context fails to start (FR-SEC-3 / AC-SEC-3). The error message
     * names both the env var ({@code APP_AUTH_JWT_SECRET}) and the
     * deployment doc path so operators can self-rescue.
     *
     * @param secret value of {@code app.auth.jwt.secret} (env-backed)
     * @param clock injected {@link Clock} (defaults to the
     *     {@code adminClock} bean defined in {@code TimeConfig} —
     *     {@code Asia/Seoul})
     */
    public JwtService(
            @Value("${app.auth.jwt.secret:}") String secret,
            Clock clock
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "APP_AUTH_JWT_SECRET must be set and >= 32 bytes; see docs/deployment/env.md");
        }
        this.key   = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    /**
     * Issue a compact-serialized HS256 JWT carrying {@code sub=userId,
     * role, name, iat, exp} with a fixed 7-day TTL (FR-2 / FR-4).
     */
    public String issue(String userId, String role, String name) {
        Instant now = clock.instant();
        Instant exp = now.plusSeconds(TTL_SECONDS);
        return Jwts.builder()
                .header().type("JWT").and()
                .subject(userId)
                .claim("role", role)
                .claim("name", name)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verify a token's signature + expiry against the configured secret
     * and {@link Clock} (FR-3 / AC-6). Any failure — bad signature,
     * expired exp, malformed structure, missing required claim — is
     * collapsed into {@code AuthException(INVALID_TOKEN)} so the wire
     * code is stable.
     */
    public JwtVerification verify(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            Claims c = jws.getPayload();
            String sub  = c.getSubject();
            String role = c.get("role", String.class);
            String name = c.get("name", String.class);
            Date   exp  = c.getExpiration();
            if (sub == null || sub.isBlank() || role == null || exp == null) {
                throw new AuthException(AuthErrorCode.INVALID_TOKEN);
            }
            return new JwtVerification(sub, role, name, exp.toInstant());
        } catch (ExpiredJwtException | SignatureException | MalformedJwtException ex) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        } catch (JwtException | IllegalArgumentException ex) {
            // Catch-all for any other jjwt parse failure.
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
