package com.connexa.api.domain.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationItemTest {

    @Test
    void markingAnUnreadNotificationReadPreservesItsIdentityAndContent() {
        Instant createdAt = Instant.parse("2026-08-05T10:00:00Z");
        NotificationItem item = new NotificationItem(
                UUID.randomUUID(),
                NotificationType.EVENT_REMINDER,
                "Event reminder",
                "Your event starts soon.",
                UUID.randomUUID(),
                createdAt,
                null);

        assertFalse(item.read());
        NotificationItem read = item.markRead(Instant.parse("2026-08-05T10:05:00Z"));
        assertTrue(read.read());
        assertEquals(item.id(), read.id());
        assertEquals(item.body(), read.body());
    }
}
