package com.connexa.api.api.v1;

import com.connexa.api.domain.notification.NotificationItem;
import com.connexa.api.domain.notification.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        UUID eventId,
        Instant createdAt,
        boolean read,
        Instant readAt) {

    public static NotificationResponse from(NotificationItem item) {
        return new NotificationResponse(
                item.id(),
                item.type(),
                item.title(),
                item.body(),
                item.eventId(),
                item.createdAt(),
                item.read(),
                item.readAt());
    }
}
