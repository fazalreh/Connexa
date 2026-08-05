package com.connexa.mobile.feature.notifications;

import static org.junit.Assert.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Test;

public class NotificationTimeFormatterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);

    @Test
    public void formatsShortElapsedTimes() {
        assertEquals("Just now", NotificationTimeFormatter.formatRelative(
                Instant.parse("2026-08-05T11:59:30Z"), CLOCK));
        assertEquals("5 min ago", NotificationTimeFormatter.formatRelative(
                Instant.parse("2026-08-05T11:55:00Z"), CLOCK));
        assertEquals("2 hr ago", NotificationTimeFormatter.formatRelative(
                Instant.parse("2026-08-05T10:00:00Z"), CLOCK));
    }

    @Test
    public void formatsOlderElapsedTimes() {
        assertEquals("Yesterday", NotificationTimeFormatter.formatRelative(
                Instant.parse("2026-08-04T12:00:00Z"), CLOCK));
        assertEquals("3 days ago", NotificationTimeFormatter.formatRelative(
                Instant.parse("2026-08-02T12:00:00Z"), CLOCK));
    }
}
