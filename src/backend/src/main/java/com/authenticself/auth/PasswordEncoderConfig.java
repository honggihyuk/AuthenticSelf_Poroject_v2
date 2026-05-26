package com.authenticself.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Auth-package configuration: BCrypt password encoder (FR-6) and the
 * filter-registration for {@link JwtAuthFilter} (FR-11).
 *
 * <p>{@link BCryptPasswordEncoder} is constructed with strength=10 —
 * Spring Security's default. This balances ~80ms of hash time on a
 * modern x86 server against forward-compatibility headroom inside the
 * V11 {@code users.password_hash} VARCHAR(72) column. The encoder
 * surface is the {@code PasswordEncoder} interface so a future bump
 * (e.g. {@code DelegatingPasswordEncoder} for prefix-based scheme
 * upgrades) drops in without touching {@link AuthService}.
 *
 * <p>The {@link JwtAuthFilter} bean is registered via
 * {@link FilterRegistrationBean} so we can pin its order and URL pattern
 * without coupling it to Spring's auto-detection of {@code @Component}
 * filters. Order = {@code HIGHEST_PRECEDENCE + 100} — runs after
 * {@code CharacterEncodingFilter} (precedence -100..0) but before any
 * business filter / dispatcher.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Registers {@link JwtAuthFilter} for the {@code /api/v1/*} pattern.
     * The filter internally bypasses the login endpoint and OPTIONS
     * preflights — see its Javadoc for the full skip-list (FR-11).
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtService jwtService, ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new JwtAuthFilter(jwtService, objectMapper));
        reg.addUrlPatterns("/api/v1/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        reg.setName("jwtAuthFilter");
        return reg;
    }
}
