package com.authenticself;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entrypoint for the AuthenticSelf backend.
 * <p>
 * Flyway runs automatically on startup against the datasource configured
 * in {@code application.yml} (env-var driven — no hardcoded secrets).
 * <p>
 * {@link EnableScheduling} powers {@link com.authenticself.ai.AnalysisPoller}
 * (FR-14). The poller bean itself is gated by {@code app.ai.poller.enabled}
 * so scheduling-the-annotation-but-disabling-the-bean is the default in
 * test profiles.
 */
@SpringBootApplication
@EnableScheduling
public class AuthenticSelfApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthenticSelfApplication.class, args);
    }
}
