package com.connexa.mobile.feature.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class CalendarMonthGridTest {

    @Test
    public void createsStableMondayFirstSixWeekGrid() {
        YearMonth january2026 = YearMonth.of(2026, 1);
        LocalDate selectedDate = LocalDate.of(2026, 1, 5);
        Map<LocalDate, Integer> eventCounts = new HashMap<>();
        eventCounts.put(selectedDate, 2);

        CalendarMonthGrid grid = CalendarMonthGrid.create(
                january2026,
                selectedDate,
                DayOfWeek.MONDAY,
                eventCounts);

        assertEquals(CalendarMonthGrid.CELL_COUNT, grid.getCells().size());
        assertTrue(grid.getCells().get(0).isBlank());
        assertTrue(grid.getCells().get(1).isBlank());
        assertTrue(grid.getCells().get(2).isBlank());
        assertEquals(LocalDate.of(2026, 1, 1), grid.getCells().get(3).getDate().get());
        assertTrue(grid.getCells().get(7).isSelected());
        assertEquals(2, grid.getCells().get(7).getEventCount());
        assertFalse(grid.getCells().get(7).isBlank());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSelectionOutsideVisibleMonth() {
        CalendarMonthGrid.create(
                YearMonth.of(2026, 1),
                LocalDate.of(2026, 2, 1),
                DayOfWeek.MONDAY,
                new HashMap<>());
    }
}
