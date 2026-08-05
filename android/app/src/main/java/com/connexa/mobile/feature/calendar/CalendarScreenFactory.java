package com.connexa.mobile.feature.calendar;

import com.connexa.mobile.core.events.EventSummary;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a complete calendar screen from API event summaries without coupling the UI to a vendor
 * database or transport implementation.
 */
public final class CalendarScreenFactory {

    private final DayOfWeek firstDayOfWeek;

    public CalendarScreenFactory() {
        this(DayOfWeek.MONDAY);
    }

    public CalendarScreenFactory(DayOfWeek firstDayOfWeek) {
        this.firstDayOfWeek = Objects.requireNonNull(firstDayOfWeek, "firstDayOfWeek is required");
    }

    public CalendarScreen create(
            YearMonth visibleMonth,
            LocalDate selectedDate,
            Collection<EventSummary> events) {
        Objects.requireNonNull(visibleMonth, "visibleMonth is required");
        Objects.requireNonNull(selectedDate, "selectedDate is required");
        Objects.requireNonNull(events, "events are required");
        if (!YearMonth.from(selectedDate).equals(visibleMonth)) {
            throw new IllegalArgumentException("selectedDate must be inside visibleMonth");
        }

        Map<LocalDate, Integer> eventCounts = CalendarEventSelector.eventCountsForMonth(
                visibleMonth,
                events);
        CalendarMonthGrid grid = CalendarMonthGrid.create(
                visibleMonth,
                selectedDate,
                firstDayOfWeek,
                eventCounts);
        return new CalendarScreen(
                visibleMonth,
                selectedDate,
                grid,
                CalendarEventSelector.eventsOn(selectedDate, events));
    }
}
