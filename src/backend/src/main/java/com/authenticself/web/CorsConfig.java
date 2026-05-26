package com.authenticself.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("X-User-Id", "Authorization", "Content-Type", "Accept")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
