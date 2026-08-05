package com.connexa.mobile.feature.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.notifications.NotificationItem;
import com.connexa.mobile.core.notifications.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class NotificationInboxRowsTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);

    @Test
    public void createsRecentAndEarlierSectionsWithNewestItemsFirst() {
        NotificationItem recentOlder = notification(
                "4c9e23f0-7a0f-4f50-a658-48620e67f2e2", "2026-08-05T09:00:00Z");
        NotificationItem recentNewer = notification(
                "aaf07d16-3ae2-41d9-a7d5-b6f1004e0e1e", "2026-08-05T11:00:00Z");
        NotificationItem earlier = notification(
                "a3fe7534-a882-43f7-af0b-c59d86da424d", "2026-08-03T12:00:00Z");

        List<NotificationInboxRow> rows = NotificationInboxRows.from(
                Arrays.asList(recentOlder, earlier, recentNewer), CLOCK);

        assertEquals(5, rows.size());
        assertTrue(rows.get(0) instanceof NotificationInboxRow.SectionRow);
        assertEquals("Notifications", ((NotificationInboxRow.SectionRow) rows.get(0)).getTitle());
        assertEquals(recentNewer, ((NotificationInboxRow.NotificationRow) rows.get(1)).getNotification());
        assertEquals(recentOlder, ((NotificationInboxRow.NotificationRow) rows.get(2)).getNotification());
        assertEquals("Earlier", ((NotificationInboxRow.SectionRow) rows.get(3)).getTitle());
        assertEquals(earlier, ((NotificationInboxRow.NotificationRow) rows.get(4)).getNotification());
    }

    @Test
    public void treatsTheTwentyFourHourBoundaryAsRecent() {
        NotificationItem boundaryItem = notification(
                "8dac92ed-f0f2-4ce4-babc-0903b67d4143", "2026-08-04T12:00:00Z");

        List<NotificationInboxRow> rows = NotificationInboxRows.from(Arrays.asList(boundaryItem), CLOCK);

        assertEquals("Notifications", ((NotificationInboxRow.SectionRow) rows.get(0)).getTitle());
    }

    private static NotificationItem notification(String id, String occurredAt) {
        return new NotificationItem(
                UUID.fromString(id),
                NotificationType.NEW_EVENT,
                "New event",
                "A community event is available.",
                UUID.fromString("908a87ee-daf7-471c-91fe-fc0cfd2af304"),
                Instant.parse(occurredAt),
                true);
    }
}
