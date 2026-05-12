package com.authenticself.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Phase A — serves curated furniture assets (rendered preview JPG + GLB 3D model)
 * directly from {@code <repo>/src/backend/var/static/furniture/} under
 * {@code /static/furniture/**}.
 *
 * <p>In production this path is taken over by Nginx (or equivalent CDN); the
 * Spring handler stays defined so dev / single-binary deployments keep working
 * without infrastructure changes.</p>
 *
 * <p>Cache-Control: 1y immutable — the filename embeds the furniture_id, and
 * any visual revision should land under a new id rather than mutating in place.</p>
 */
@Configuration
public class StaticAssetsWebConfig implements WebMvcConfigurer {

    private final String furnitureRoot;

    public StaticAssetsWebConfig(
            @Value("${app.static.furniture-root:./var/static/furniture}") String furnitureRoot
    ) {
        this.furnitureRoot = furnitureRoot;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path absolute = Paths.get(furnitureRoot).toAbsolutePath().normalize();
        String location = absolute.toUri().toString();   // file:/.../ — trailing slash preserved
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/static/furniture/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
