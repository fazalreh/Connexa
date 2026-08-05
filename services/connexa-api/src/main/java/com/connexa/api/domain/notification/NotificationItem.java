package com.connexa.api.domain.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity-scoped notification read model.
 */
public record NotificationItem(
        UUID id,
        NotificationType type,
        String title,
        String body,
        UUID eventId,
        Instant createdAt,
        Instant readAt) {

    public NotificationItem {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(type, "type is required");
        title = requireText(title, "title", 160);
        body = requireText(body, "body", 2_000);
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (readAt != null && readAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("readAt cannot be before createdAt");
        }
    }

    public boolean read() {
        return readAt != null;
    }

    public NotificationItem markRead(Instant timestamp) {
        Objects.requireNonNull(timestamp, "timestamp is required");
        if (read()) {
            return this;
        }
        return new NotificationItem(id, type, title, body, eventId, createdAt, timestamp);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
