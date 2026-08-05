package com.connexa.mobile.feature.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.events.EventSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

public final class CalendarEventSelectorTest {

    @Test
    public void usesEventTimeZoneRatherThanDeviceTimeZone() {
        EventSummary event = event(
                "PUBLISHED",
                "2026-05-01T19:30:00Z",
                "2026-05-01T20:30:00Z",
                "Asia/Karachi");

        assertFalse(CalendarEventSelector.occursOn(event, LocalDate.of(2026, 5, 1)));
        assertTrue(CalendarEventSelector.occursOn(event, LocalDate.of(2026, 5, 2)));
    }

    @Test
    public void countsAnOvernightEventOnBothCalendarDates() {
        EventSummary overnightEvent = event(
                "PUBLISHED",
                "2026-05-01T18:00:00Z",
                "2026-05-01T20:00:00Z",
                "Asia/Karachi");

        Map<LocalDate, Integer> counts = CalendarEventSelector.eventCountsForMonth(
                YearMonth.of(2026, 5),
                Arrays.asList(overnightEvent));

        assertEquals(Integer.valueOf(1), counts.get(LocalDate.of(2026, 5, 1)));
        assertEquals(Integer.valueOf(1), counts.get(LocalDate.of(2026, 5, 2)));
    }

    @Test
    public void treatsTheEndInstantAsExclusiveAndExcludesCancelledEvents() {
        EventSummary endsAtMidnight = event(
                "PUBLISHED",
                "2026-05-01T17:00:00Z",
                "2026-05-01T19:00:00Z",
                "Asia/Karachi");
        EventSummary cancelled = event(
                "CANCELLED",
                "2026-05-01T18:00:00Z",
                "2026-05-01T20:00:00Z",
                "Asia/Karachi");

        assertTrue(CalendarEventSelector.occursOn(endsAtMidnight, LocalDate.of(2026, 5, 1)));
        assertFalse(CalendarEventSelector.occursOn(endsAtMidnight, LocalDate.of(2026, 5, 2)));
        List<EventSummary> events = CalendarEventSelector.eventsOn(
                LocalDate.of(2026, 5, 1),
                Arrays.asList(endsAtMidnight, cancelled));
        assertEquals(1, events.size());
        assertEquals(endsAtMidnight.getId(), events.get(0).getId());
    }

    private static EventSummary event(
            String status,
            String startsAt,
            String endsAt,
            String timeZone) {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new EventSummary(
                UUID.randomUUID(),
                "Community workshop",
                "A calendar test event",
                Instant.parse(startsAt),
                Instant.parse(endsAt),
                timeZone,
                "Community hall",
                "Workshop",
                "Connexa",
                status,
                0,
                createdAt,
                createdAt);
    }
}
