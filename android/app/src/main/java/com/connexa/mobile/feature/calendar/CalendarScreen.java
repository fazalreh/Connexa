package com.connexa.mobile.feature.calendar;

import com.connexa.mobile.core.events.EventSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable view model for one rendered calendar screen.
 */
public final class CalendarScreen {

    private final YearMonth visibleMonth;
    private final LocalDate selectedDate;
    private final CalendarMonthGrid monthGrid;
    private final List<EventSummary> selectedDateEvents;

    public CalendarScreen(
            YearMonth visibleMonth,
            LocalDate selectedDate,
            CalendarMonthGrid monthGrid,
            List<EventSummary> selectedDateEvents) {
        this.visibleMonth = Objects.requireNonNull(visibleMonth, "visibleMonth is required");
        this.selectedDate = Objects.requireNonNull(selectedDate, "selectedDate is required");
        this.monthGrid = Objects.requireNonNull(monthGrid, "monthGrid is required");
        this.selectedDateEvents = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(selectedDateEvents, "selectedDateEvents are required")));
        if (!YearMonth.from(selectedDate).equals(visibleMonth)) {
            throw new IllegalArgumentException("selectedDate must be inside visibleMonth");
        }
        if (!monthGrid.getMonth().equals(visibleMonth)) {
            throw new IllegalArgumentException("monthGrid must represent visibleMonth");
        }
    }

    public YearMonth getVisibleMonth() {
        return visibleMonth;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public CalendarMonthGrid getMonthGrid() {
        return monthGrid;
    }

    public List<EventSummary> getSelectedDateEvents() {
        return selectedDateEvents;
    }
}
