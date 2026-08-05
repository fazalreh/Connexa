package com.connexa.mobile.feature.calendar;

import com.connexa.mobile.core.events.EventSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Calendar-specific selection rules for event summaries returned by the Connexa API.
 *
 * <p>The API expresses event times as UTC instants and includes an IANA time zone for calendar
 * behavior. This class always evaluates day boundaries in that event time zone rather than in the
 * device's current time zone.</p>
 */
public final class CalendarEventSelector {

    private static final Comparator<EventSummary> EVENT_ORDER = Comparator
            .comparing(EventSummary::getStartsAt)
            .thenComparing(EventSummary::getEndsAt)
            .thenComparing(EventSummary::getTitle)
            .thenComparing(EventSummary::getId);

    private CalendarEventSelector() {
    }

    public static List<EventSummary> eventsOn(
            LocalDate date,
            Collection<EventSummary> events) {
        Objects.requireNonNull(date, "date is required");
        Objects.requireNonNull(events, "events are required");

        List<EventSummary> matches = new ArrayList<>();
        for (EventSummary event : events) {
            EventSummary nonNullEvent = Objects.requireNonNull(event, "events cannot contain null");
            if (isCalendarVisible(nonNullEvent) && occursOn(nonNullEvent, date)) {
                matches.add(nonNullEvent);
            }
        }
        matches.sort(EVENT_ORDER);
        return Collections.unmodifiableList(new ArrayList<>(matches));
    }

    public static Map<LocalDate, Integer> eventCountsForMonth(
            YearMonth month,
            Collection<EventSummary> events) {
        Objects.requireNonNull(month, "month is required");
        Objects.requireNonNull(events, "events are required");

        Map<LocalDate, Integer> counts = new HashMap<>();
        LocalDate date = month.atDay(1);
        LocalDate lastDate = month.atEndOfMonth();
        while (!date.isAfter(lastDate)) {
            int count = eventsOn(date, events).size();
            if (count > 0) {
                counts.put(date, count);
            }
            date = date.plusDays(1);
        }
        return Collections.unmodifiableMap(new HashMap<>(counts));
    }

    public static boolean occursOn(EventSummary event, LocalDate date) {
        Objects.requireNonNull(event, "event is required");
        Objects.requireNonNull(date, "date is required");

        ZoneId eventZone = ZoneId.of(event.getTimeZone());
        Instant startOfDay = date.atStartOfDay(eventZone).toInstant();
        Instant startOfNextDay = date.plusDays(1).atStartOfDay(eventZone).toInstant();
        return event.getStartsAt().isBefore(startOfNextDay)
                && event.getEndsAt().isAfter(startOfDay);
    }

    public static boolean isCalendarVisible(EventSummary event) {
        Objects.requireNonNull(event, "event is required");
        String normalizedStatus = event.getStatus().trim().toUpperCase(Locale.ROOT);
        return "PUBLISHED".equals(normalizedStatus) || "CHANGED".equals(normalizedStatus);
    }
}
