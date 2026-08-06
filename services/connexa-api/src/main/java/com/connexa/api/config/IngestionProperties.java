package com.connexa.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the machine-to-machine announcement intake.
 *
 * <p>The reader is a scheduled process with no human to sign in, so it authenticates with a
 * shared token rather than a user identity. Intake stays disabled until a token is set, so
 * an unconfigured deployment exposes nothing.
 */
@ConfigurationProperties(prefix = "connexa.ingestion")
public record IngestionProperties(String apiToken, String organizerName) {

    public IngestionProperties {
        organizerName = organizerName == null || organizerName.isBlank()
                ? "Campus Announcements"
                : organizerName.trim();
    }

    public boolean isEnabled() {
        return apiToken != null && !apiToken.isBlank();
    }
}
