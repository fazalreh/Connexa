package com.connexa.mobile.core.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.Test;

public class NotificationItemTest {

    @Test
    public void withReadReturnsANewItemWithoutChangingTheOriginal() {
        NotificationItem unreadItem = notification(false);

        NotificationItem readItem = unreadItem.withRead(true);

        assertFalse(unreadItem.isRead());
        assertTrue(readItem.isRead());
        assertEquals(unreadItem.getId(), readItem.getId());
    }

    @Test
    public void withReadReturnsTheSameItemWhenTheStateAlreadyMatches() {
        NotificationItem readItem = notification(true);

        assertSame(readItem, readItem.withRead(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankTitles() {
        new NotificationItem(
                UUID.randomUUID(),
                NotificationType.NEW_EVENT,
                "  ",
                "A new event is available.",
                UUID.randomUUID(),
                Instant.parse("2026-08-05T12:00:00Z"),
                false);
    }

    private static NotificationItem notification(boolean read) {
        return new NotificationItem(
                UUID.fromString("ff7a3fb7-4676-469d-8c53-48eb2b8a29cc"),
                NotificationType.RSVP_CONFIRMED,
                "RSVP confirmed",
                "You are registered for Design Studio.",
                UUID.fromString("908a87ee-daf7-471c-91fe-fc0cfd2af304"),
                Instant.parse("2026-08-05T12:00:00Z"),
                read);
    }
}
