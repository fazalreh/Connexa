package com.connexa.mobile.feature.events;

import com.connexa.mobile.core.events.EventSummary;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Formats API timestamps in the event's declared IANA time zone.
 */
public final class EventTimeFormatter {

    private static final String DATE_TIME_PATTERN = "EEE, MMM d \u00B7 h:mm a";

    private EventTimeFormatter() {
    }

    public static String formatStart(EventSummary event, Locale locale) {
        Objects.requireNonNull(event, "event is required");
        return formatter(locale)
                .withZone(ZoneId.of(event.getTimeZone()))
                .format(event.getStartsAt())
                + " "
                + event.getTimeZone();
    }

    public static String formatRange(EventSummary event, Locale locale) {
        Objects.requireNonNull(event, "event is required");
        ZoneId zoneId = ZoneId.of(event.getTimeZone());
        DateTimeFormatter formatter = formatter(locale).withZone(zoneId);
        return formatter.format(event.getStartsAt())
                + " \u2013 "
                + formatter.format(event.getEndsAt())
                + " "
                + event.getTimeZone();
    }

    private static DateTimeFormatter formatter(Locale locale) {
        return DateTimeFormatter.ofPattern(
                DATE_TIME_PATTERN,
                Objects.requireNonNull(locale, "locale is required"));
    }
}
