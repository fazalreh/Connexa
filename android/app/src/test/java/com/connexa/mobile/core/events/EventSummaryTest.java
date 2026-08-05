package com.connexa.mobile.core.events;

import static org.junit.Assert.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.Test;

public class EventSummaryTest {

    @Test
    public void rejectsAnEndTimeThatDoesNotFollowTheStartTime() {
        Instant timestamp = Instant.parse("2026-08-05T12:00:00Z");

        assertThrows(
                IllegalArgumentException.class,
                () -> new EventSummary(
                        UUID.randomUUID(),
                        "Design session",
                        "A working session for the community.",
                        timestamp,
                        timestamp,
                        "Asia/Karachi",
                        "Innovation Hub",
                        "Workshop",
                        "Connexa Community",
                        "PUBLISHED",
                        0,
                        timestamp,
                        timestamp));
    }
}
