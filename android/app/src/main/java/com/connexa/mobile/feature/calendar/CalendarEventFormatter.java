package com.connexa.mobile.feature.calendar;

import com.connexa.mobile.core.events.EventSummary;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;

/**
 * Locale-aware text formatting for the calendar presentation layer.
 */
public final class CalendarEventFormatter {

    public String formatMonth(YearMonth month, Locale locale) {
        Objects.requireNonNull(month, "month is required");
        return DateTimeFormatter.ofPattern("MMMM uuuu", requireLocale(locale)).format(month);
    }

    public String formatSelectedDate(LocalDate date, Locale locale) {
        Objects.requireNonNull(date, "date is required");
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(requireLocale(locale))
                .format(date);
    }

    public String formatTimeRange(EventSummary event, Locale locale) {
        Objects.requireNonNull(event, "event is required");
        Locale nonNullLocale = requireLocale(locale);
        ZonedDateTime startsAt = event.getStartsAt().atZone(java.time.ZoneId.of(event.getTimeZone()));
        ZonedDateTime endsAt = event.getEndsAt().atZone(java.time.ZoneId.of(event.getTimeZone()));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(nonNullLocale);

        if (startsAt.toLocalDate().equals(endsAt.toLocalDate())) {
            return timeFormatter.format(startsAt) + " \u2013 " + timeFormatter.format(endsAt);
        }

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(nonNullLocale);
        return dateTimeFormatter.format(startsAt) + " \u2013 " + dateTimeFormatter.format(endsAt);
    }

    private static Locale requireLocale(Locale locale) {
        return Objects.requireNonNull(locale, "locale is required");
    }
}
