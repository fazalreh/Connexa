package com.connexa.mobile.feature.notifications;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Formats inbox timestamps as concise relative-time labels.
 */
public final class NotificationTimeFormatter {

    private NotificationTimeFormatter() {
    }

    public static String formatRelative(Instant occurredAt, Clock clock) {
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(clock, "clock is required");

        Duration elapsed = Duration.between(occurredAt, clock.instant());
        if (elapsed.isNegative() || elapsed.compareTo(Duration.ofMinutes(1)) < 0) {
            return "Just now";
        }
        long minutes = elapsed.toMinutes();
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = elapsed.toHours();
        if (hours < 24) {
            return hours + " hr ago";
        }
        long days = elapsed.toDays();
        if (days == 1) {
            return "Yesterday";
        }
        return days + " days ago";
    }
}
