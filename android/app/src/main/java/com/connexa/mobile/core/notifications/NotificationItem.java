package com.connexa.mobile.core.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of one in-app notification supplied by the Connexa backend.
 */
public final class NotificationItem {

    private final UUID id;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final UUID relatedEventId;
    private final Instant occurredAt;
    private final boolean read;

    /**
     * Creates a notification item.
     *
     * @param relatedEventId the associated event, or {@code null} when no event can be opened
     */
    public NotificationItem(
            UUID id,
            NotificationType type,
            String title,
            String message,
            UUID relatedEventId,
            Instant occurredAt,
            boolean read) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.title = requireText(title, "title");
        this.message = requireText(message, "message");
        this.relatedEventId = relatedEventId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.read = read;
    }

    public UUID getId() {
        return id;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Returns the event that this notification refers to, or {@code null} when it is informational.
     */
    public UUID getRelatedEventId() {
        return relatedEventId;
    }

    public boolean hasRelatedEvent() {
        return relatedEventId != null;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public boolean isRead() {
        return read;
    }

    /**
     * Returns a new item with the requested read state. The original item is never changed.
     */
    public NotificationItem withRead(boolean read) {
        if (this.read == read) {
            return this;
        }
        return new NotificationItem(id, type, title, message, relatedEventId, occurredAt, read);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NotificationItem)) {
            return false;
        }
        NotificationItem that = (NotificationItem) other;
        return read == that.read
                && id.equals(that.id)
                && type == that.type
                && title.equals(that.title)
                && message.equals(that.message)
                && Objects.equals(relatedEventId, that.relatedEventId)
                && occurredAt.equals(that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, title, message, relatedEventId, occurredAt, read);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
