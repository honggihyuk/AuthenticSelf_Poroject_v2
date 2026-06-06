package com.authenticself.web;

import com.authenticself.auth.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Single source of CORS truth for the {@code /api/**} surface
 * (UC-SECURE-AUTH FR-CORS-1 / AC-CORS-1 / AC-CORS-3).
 *
 * <p>Replaces the per-controller cross-origin annotation blocks that
 * previously lived on {@code SpaceController}, {@code WishlistController},
 * {@code AdminController}, and {@code AuthController} (FR-CORS-2).
 * Wildcard origins are unsafe in combination with credentialed requests
 * (Authorization headers); this config pins the allow-list to an
 * env-driven comma-separated property.
 *
 * <p>CORS is applied as a servlet-level {@link CorsFilter} ordered at
 * {@link Ordered#HIGHEST_PRECEDENCE} — i.e. <em>before</em>
 * {@link JwtAuthFilter} (HIGHEST_PRECEDENCE + 100). This matters because
 * {@code JwtAuthFilter} short-circuits unauthenticated {@code /api/v1/**}
 * requests with a 401 and never calls {@code chain.doFilter}, so those
 * responses never reach the {@code DispatcherServlet}. A WebMvc-level
 * CORS mapping ({@code addCorsMappings}) only decorates responses that
 * do reach the dispatcher, leaving the 401 without an
 * {@code Access-Control-Allow-Origin} header — which the browser surfaces
 * as a misleading CORS error instead of the real 401. Running CORS as a
 * filter ahead of the auth filter fixes both the preflight and the
 * credentialed actual request, including rejections.
 *
 * <p>Dev defaults cover Metro on port 8082 and the legacy 8081 that
 * {@code dev_servers.md} still documents. Production deploys override
 * the {@code APP_CORS_ALLOWED_ORIGINS} env var with the public origins.
 *
 * <p>Allowed methods include {@code PUT} because
 * {@code SpaceController.setPreferredStyle} uses it; the prior
 * per-controller config already permitted it and that behavior is
 * preserved here.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("X-User-Id", "Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        FilterRegistrationBean<CorsFilter> reg = new FilterRegistrationBean<>(new CorsFilter(source));
        // Must run before JwtAuthFilter (HIGHEST_PRECEDENCE + 100) so that
        // filter-level 401 rejections still carry the CORS headers.
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.setName("corsFilter");
        return reg;
    }
}
