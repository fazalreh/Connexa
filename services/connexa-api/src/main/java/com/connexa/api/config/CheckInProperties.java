package com.connexa.api.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for door check-in.
 *
 * <p>Check-in stays disabled until a signing secret is configured. An unset secret must never
 * fall back to a default, because a predictable key makes every pass forgeable.
 */
@ConfigurationProperties(prefix = "connexa.checkin")
public record CheckInProperties(String secret, Duration passValidity) {

    public CheckInProperties {
        // Short enough that a screenshot of someone's code is not a reusable ticket,
        // long enough to survive a queue at the door.
        passValidity = passValidity == null || passValidity.isZero() || passValidity.isNegative()
                ? Duration.ofMinutes(5)
                : passValidity;
    }

    public boolean isEnabled() {
        return secret != null && !secret.isBlank();
    }

    public byte[] secretBytes() {
        return secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }
}
