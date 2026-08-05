package com.connexa.mobile.feature.events;

import static org.junit.Assert.assertEquals;

import com.connexa.mobile.core.events.EventSummary;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.Test;

public class EventTimeFormatterTest {

    @Test
    public void rendersTheStartInTheEventTimeZone() {
        EventSummary event = eventSummary();

        assertEquals(
                "Sun, Aug 9 \u00B7 5:00 PM Asia/Karachi",
                EventTimeFormatter.formatStart(event, Locale.US));
    }

    @Test
    public void rendersTheFullRangeInTheEventTimeZone() {
        EventSummary event = eventSummary();

        assertEquals(
                "Sun, Aug 9 \u00B7 5:00 PM \u2013 Sun, Aug 9 \u00B7 6:00 PM Asia/Karachi",
                EventTimeFormatter.formatRange(event, Locale.US));
    }

    private static EventSummary eventSummary() {
        Instant createdAt = Instant.parse("2026-08-01T12:00:00Z");
        return new EventSummary(
                UUID.fromString("b50ed025-5ea4-4efa-a7ea-35cd91e75570"),
                "Design session",
                "A working session for the community.",
                Instant.parse("2026-08-09T12:00:00Z"),
                Instant.parse("2026-08-09T13:00:00Z"),
                "Asia/Karachi",
                "Innovation Hub",
                "Workshop",
                "Connexa Community",
                "PUBLISHED",
                0,
                createdAt,
                createdAt);
    }
}
