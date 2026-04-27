package com.authenticself.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the type-safe configuration classes so they are available for
 * DI without field-level {@code @ConfigurationPropertiesScan}.
 */
@Configuration
@EnableConfigurationProperties({ UploadProperties.class, StorageProperties.class })
public class ApplicationConfig {
}
