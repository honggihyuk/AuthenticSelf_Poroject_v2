package com.authenticself.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots a minimal Spring context wiring only {@link JwtService} to
 * exercise UC-SECURE-AUTH FR-SEC-3 / AC-SEC-3 — the bean constructor
 * MUST throw {@link IllegalStateException} when {@code APP_AUTH_JWT_SECRET}
 * is unset or shorter than 32 bytes, with a message that names both
 * the env var and the {@code docs/deployment/env.md} pointer.
 *
 * <p>Uses a stand-alone {@link AnnotationConfigApplicationContext}
 * (no {@code @SpringBootTest}) so the test never touches the datasource
 * or any other auto-configured bean — failure is attributed unambiguously
 * to the {@link JwtService} constructor.
 */
class JwtSecretStartupFailureTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-04-18T09:00:00Z"), ZoneOffset.UTC);

    @Configuration
    static class JwtOnlyConfig {
        @Bean Clock clock() { return CLOCK; }
        // The secret is supplied via @Value lookup at construction time;
        // we leave the property unset to trigger the failure path.
        @Bean JwtService jwtService(Clock c) {
            // Force constructor to receive null (simulates @Value with no env, no default).
            return new JwtService(null, c);
        }
    }

    @Configuration
    static class JwtShortSecretConfig {
        @Bean Clock clock() { return CLOCK; }
        @Bean JwtService jwtService(Clock c) {
            return new JwtService("short-16-bytes!!", c);
        }
    }

    @Test
    @DisplayName("AC-SEC-3: missing secret → IllegalStateException containing APP_AUTH_JWT_SECRET and docs/deployment/env.md")
    void missingSecretFailsBoot_acSec3() {
        assertThatThrownBy(() -> {
            try (AnnotationConfigApplicationContext ctx =
                         new AnnotationConfigApplicationContext(JwtOnlyConfig.class)) {
                // never reached
            }
        }).satisfies(t -> {
            // Walk the cause chain — Spring wraps bean creation failures.
            Throwable cur = t;
            while (cur != null) {
                if (cur instanceof IllegalStateException && cur.getMessage() != null) {
                    assertThat(cur.getMessage()).contains("APP_AUTH_JWT_SECRET");
                    assertThat(cur.getMessage()).contains("docs/deployment/env.md");
                    return;
                }
                cur = cur.getCause();
            }
            throw new AssertionError("No IllegalStateException with the expected message in chain: " + t);
        });
    }

    @Test
    @DisplayName("AC-SEC-3: 16-byte secret → IllegalStateException with same message contract")
    void shortSecretFailsBoot_acSec3() {
        assertThatThrownBy(() -> {
            try (AnnotationConfigApplicationContext ctx =
                         new AnnotationConfigApplicationContext(JwtShortSecretConfig.class)) {
                // never reached
            }
        }).satisfies(t -> {
            Throwable cur = t;
            while (cur != null) {
                if (cur instanceof IllegalStateException && cur.getMessage() != null) {
                    assertThat(cur.getMessage()).contains("APP_AUTH_JWT_SECRET");
                    assertThat(cur.getMessage()).contains("docs/deployment/env.md");
                    return;
                }
                cur = cur.getCause();
            }
            throw new AssertionError("No IllegalStateException with the expected message in chain: " + t);
        });
    }
}
