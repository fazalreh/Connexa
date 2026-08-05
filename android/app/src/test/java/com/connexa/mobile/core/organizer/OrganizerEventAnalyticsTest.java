package com.connexa.mobile.core.organizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.Test;

public class OrganizerEventAnalyticsTest {

    @Test
    public void retainsAValidAnalyticsSnapshot() {
        UUID eventId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-09-10T11:00:00Z");

        OrganizerEventAnalytics analytics = new OrganizerEventAnalytics(
                eventId,
                "Design workshop",
                80,
                60,
                35,
                4,
                observedAt);

        assertEquals(eventId, analytics.getEventId());
        assertEquals(80, analytics.getCapacity());
        assertEquals(60, analytics.getRegisteredCount());
        assertEquals(35, analytics.getCheckedInCount());
        assertEquals(4, analytics.getWaitlistedCount());
        assertEquals(observedAt, analytics.getObservedAt());
    }

    @Test
    public void rejectsCheckInsThatExceedRegistrations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrganizerEventAnalytics(
                        UUID.randomUUID(),
                        "Design workshop",
                        80,
                        20,
                        21,
                        0,
                        Instant.parse("2026-09-10T11:00:00Z")));
    }

    @Test
    public void rejectsRegistrationsThatExceedCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrganizerEventAnalytics(
                        UUID.randomUUID(),
                        "Design workshop",
                        80,
                        81,
                        0,
                        0,
                        Instant.parse("2026-09-10T11:00:00Z")));
    }
}
