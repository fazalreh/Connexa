package com.connexa.api.domain.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class EventWindowTest {

    @Test
    void acceptsAnEndTimeAfterTheStartTime() {
        assertDoesNotThrow(() -> new EventWindow(
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-05T11:00:00Z"),
                ZoneId.of("Asia/Karachi")));
    }

    @Test
    void rejectsAnEndTimeThatIsNotAfterTheStartTime() {
        assertThrows(IllegalArgumentException.class, () -> new EventWindow(
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-05T10:00:00Z"),
                ZoneId.of("Asia/Karachi")));
    }
}
