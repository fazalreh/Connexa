package com.connexa.mobile.feature.calendar;

import java.time.LocalDate;
import java.util.Optional;

/**
 * One stable position in a six-week calendar grid.
 *
 * <p>A cell is either a real day from the visible month or a blank leading/trailing position.
 * Blank positions deliberately do not carry a date, selection, or event count.</p>
 */
public final class CalendarDayCell {

    private final LocalDate date;
    private final boolean selected;
    private final int eventCount;

    private CalendarDayCell(LocalDate date, boolean selected, int eventCount) {
        this.date = date;
        this.selected = selected;
        this.eventCount = eventCount;
    }

    public static CalendarDayCell blank() {
        return new CalendarDayCell(null, false, 0);
    }

    public static CalendarDayCell forDate(LocalDate date, boolean selected, int eventCount) {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (eventCount < 0) {
            throw new IllegalArgumentException("eventCount must be zero or greater");
        }
        return new CalendarDayCell(date, selected, eventCount);
    }

    public boolean isBlank() {
        return date == null;
    }

    public Optional<LocalDate> getDate() {
        return Optional.ofNullable(date);
    }

    public boolean isSelected() {
        return selected;
    }

    public int getEventCount() {
        return eventCount;
    }
}
