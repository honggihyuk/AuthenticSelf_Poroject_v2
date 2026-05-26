package com.authenticself.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService} covering UC-SECURE-AUTH
 * FR-2 / FR-3 / FR-4 / FR-5 (AC-2..AC-6).
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-please-replace-in-production-32chars";
    private static final String OTHER_SECRET = "another-32byte-secret-for-bad-sig-cases-zzzz";

    private static Clock fixedClock(String iso) {
        return Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
    }

    // -----------------------------------------------------------------
    // AC-3 — issue() emits HS256 JWT with expected header + claims.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-3: issue emits HS256 JWT with sub/role/name/iat/exp claims")
    void issue_emitsHs256JwtWithExpectedClaims_ac3() {
        Clock clock = fixedClock("2026-04-18T09:00:00Z");
        JwtService svc = new JwtService(SECRET, clock);

        String token = svc.issue("user", "USER", "일반사용자");
        assertThat(token).isNotBlank();

        // Parse with the SAME fixed clock so exp validation does not race the
        // real wall clock — the token's exp is in 2026-04-25 which may be in
        // the past relative to the test-host system clock at any given run.
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(jws.getHeader().getType()).isEqualTo("JWT");
        Claims c = jws.getPayload();
        assertThat(c.getSubject()).isEqualTo("user");
        assertThat(c.get("role", String.class)).isEqualTo("USER");
        assertThat(c.get("name", String.class)).isEqualTo("일반사용자");
        assertThat(c.getIssuedAt()).isNotNull();
        assertThat(c.getExpiration()).isNotNull();
    }

    // -----------------------------------------------------------------
    // AC-4 — TTL is exactly 7 days (604800s).
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-4: exp - iat == 604800s exactly")
    void ttlIsExactly7Days_ac4() {
        Clock clock = fixedClock("2026-04-18T09:00:00Z");
        JwtService svc = new JwtService(SECRET, clock);

        String token = svc.issue("u", "USER", "n");
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims c = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token).getPayload();
        long iat = c.getIssuedAt().toInstant().getEpochSecond();
        long exp = c.getExpiration().toInstant().getEpochSecond();
        assertThat(exp - iat).isEqualTo(604_800L);
    }

    // -----------------------------------------------------------------
    // AC-5 — statelessness + determinism across instances.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-5: two instances with same secret mutually verify; identical clock → identical token bytes")
    void statelessVerifyAcrossInstances_ac5() {
        Clock clock = fixedClock("2026-04-18T09:00:00Z");
        JwtService a = new JwtService(SECRET, clock);
        JwtService b = new JwtService(SECRET, clock);

        String tokenFromA = a.issue("user", "USER", "이름");
        String tokenFromB = b.issue("user", "USER", "이름");

        // HMAC-SHA256 is deterministic — same secret + same clock + same payload = same bytes.
        assertThat(tokenFromA).isEqualTo(tokenFromB);

        // And each instance verifies tokens from the other.
        JwtVerification va = b.verify(tokenFromA);
        JwtVerification vb = a.verify(tokenFromB);
        assertThat(va.userId()).isEqualTo("user");
        assertThat(vb.userId()).isEqualTo("user");
    }

    // -----------------------------------------------------------------
    // AC-6 — verify() failure modes all map to AuthException(INVALID_TOKEN).
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-6: bad signature → AuthException(INVALID_TOKEN)")
    void verifyFailureModes_ac6_badSignature() {
        Clock clock = fixedClock("2026-04-18T09:00:00Z");
        JwtService issuer   = new JwtService(OTHER_SECRET, clock);
        JwtService verifier = new JwtService(SECRET,       clock);
        String tokenSignedWithOtherSecret = issuer.issue("u", "USER", "n");

        assertThatThrownBy(() -> verifier.verify(tokenSignedWithOtherSecret))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).code())
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("AC-6: expired token → AuthException(INVALID_TOKEN)")
    void verifyFailureModes_ac6_expired() {
        Clock issueClock  = fixedClock("2026-04-18T09:00:00Z");
        Clock verifyClock = fixedClock("2026-05-30T09:00:00Z"); // > 7 days later
        JwtService issuer   = new JwtService(SECRET, issueClock);
        JwtService verifier = new JwtService(SECRET, verifyClock);

        String token = issuer.issue("u", "USER", "n");

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).code())
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("AC-6: malformed string → AuthException(INVALID_TOKEN)")
    void verifyFailureModes_ac6_malformed() {
        JwtService svc = new JwtService(SECRET, fixedClock("2026-04-18T09:00:00Z"));
        assertThatThrownBy(() -> svc.verify("not.a.jwt"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).code())
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("AC-6: token missing sub claim → AuthException(INVALID_TOKEN)")
    void verifyFailureModes_ac6_missingSub() {
        // Hand-craft a token with no sub claim, signed with the same secret.
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.parse("2026-04-18T09:00:00Z");
        String tokenNoSub = Jwts.builder()
                .header().type("JWT").and()
                .claim("role", "USER")
                .claim("name", "n")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        JwtService svc = new JwtService(SECRET, fixedClock("2026-04-18T09:00:30Z"));

        assertThatThrownBy(() -> svc.verify(tokenNoSub))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).code())
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    // -----------------------------------------------------------------
    // FR-SEC-3 / AC-SEC-3 — fail-fast on missing or short secret.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-SEC-3: null secret → IllegalStateException mentioning APP_AUTH_JWT_SECRET + docs/deployment/env.md")
    void failFast_nullSecret() {
        assertThatThrownBy(() -> new JwtService(null, fixedClock("2026-04-18T09:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_JWT_SECRET")
                .hasMessageContaining("docs/deployment/env.md");
    }

    @Test
    @DisplayName("AC-SEC-3: short secret (16 bytes) → IllegalStateException")
    void failFast_shortSecret() {
        assertThatThrownBy(() -> new JwtService("short-16-bytes!!", fixedClock("2026-04-18T09:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_JWT_SECRET")
                .hasMessageContaining("docs/deployment/env.md");
    }

    @Test
    @DisplayName("AC-SEC-3: empty secret → IllegalStateException")
    void failFast_emptySecret() {
        assertThatThrownBy(() -> new JwtService("", fixedClock("2026-04-18T09:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_AUTH_JWT_SECRET");
    }
}
