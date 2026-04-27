package com.authenticself.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Configuration for the admin-package {@link Clock} bean
 * (UC-03-admin-overview FR-10 / FR-14 / AC-32 / AC-42).
 * <p>
 * No other module defines a {@code Clock} bean at present, so this is the
 * canonical wall-clock source for the admin surface. The time zone is
 * driven by {@code app.admin.time-zone} (default {@code Asia/Seoul}, env
 * override {@code ADMIN_TIME_ZONE} via the standard Spring property
 * chain) per FR-14 / AC-42.
 *
 * <p>Tests can override the bean by declaring a {@code @Primary} Clock in
 * a {@code @TestConfiguration}; the production bean is deliberately NOT
 * marked {@code @Primary} so a test-scoped override does not need a
 * qualifier.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock adminClock(@Value("${app.admin.time-zone:Asia/Seoul}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
