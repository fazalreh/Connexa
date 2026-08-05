package com.connexa.mobile.feature.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A fixed six-row month grid suitable for a seven-column calendar UI.
 *
 * <p>A fixed size prevents the screen from jumping when navigating between months with different
 * week counts. The first column is configurable so the feature can follow the community's chosen
 * week convention.</p>
 */
public final class CalendarMonthGrid {

    public static final int DAYS_PER_WEEK = 7;
    public static final int WEEK_COUNT = 6;
    public static final int CELL_COUNT = DAYS_PER_WEEK * WEEK_COUNT;

    private final YearMonth month;
    private final DayOfWeek firstDayOfWeek;
    private final List<CalendarDayCell> cells;

    private CalendarMonthGrid(
            YearMonth month,
            DayOfWeek firstDayOfWeek,
            List<CalendarDayCell> cells) {
        this.month = month;
        this.firstDayOfWeek = firstDayOfWeek;
        this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
    }

    public static CalendarMonthGrid create(
            YearMonth month,
            LocalDate selectedDate,
            DayOfWeek firstDayOfWeek,
            Map<LocalDate, Integer> eventCounts) {
        Objects.requireNonNull(month, "month is required");
        Objects.requireNonNull(selectedDate, "selectedDate is required");
        Objects.requireNonNull(firstDayOfWeek, "firstDayOfWeek is required");
        Objects.requireNonNull(eventCounts, "eventCounts is required");
        if (!YearMonth.from(selectedDate).equals(month)) {
            throw new IllegalArgumentException("selectedDate must be inside month");
        }

        int leadingBlankCount = Math.floorMod(
                month.atDay(1).getDayOfWeek().getValue() - firstDayOfWeek.getValue(),
                DAYS_PER_WEEK);
        List<CalendarDayCell> cells = new ArrayList<>(CELL_COUNT);

        for (int index = 0; index < CELL_COUNT; index++) {
            int dayOfMonth = index - leadingBlankCount + 1;
            if (dayOfMonth < 1 || dayOfMonth > month.lengthOfMonth()) {
                cells.add(CalendarDayCell.blank());
                continue;
            }

            LocalDate date = month.atDay(dayOfMonth);
            Integer count = eventCounts.get(date);
            int eventCount = count == null ? 0 : count;
            cells.add(CalendarDayCell.forDate(date, date.equals(selectedDate), eventCount));
        }

        return new CalendarMonthGrid(month, firstDayOfWeek, cells);
    }

    public YearMonth getMonth() {
        return month;
    }

    public DayOfWeek getFirstDayOfWeek() {
        return firstDayOfWeek;
    }

    public List<CalendarDayCell> getCells() {
        return cells;
    }

    public Optional<CalendarDayCell> findCell(LocalDate date) {
        if (date == null || !YearMonth.from(date).equals(month)) {
            return Optional.empty();
        }
        return cells.stream()
                .filter(cell -> cell.getDate().map(date::equals).orElse(false))
                .findFirst();
    }
}
