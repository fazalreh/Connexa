package com.connexa.mobile.core.attendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.Test;

public class AttendanceStateTest {

    @Test
    public void representsAnEmptyStateWithoutTimestamp() {
        UUID eventId = UUID.randomUUID();

        AttendanceState state = AttendanceState.empty(eventId);

        assertEquals(eventId, state.getEventId());
        assertFalse(state.isSaved());
        assertNull(state.getRsvpStatus());
        assertNull(state.getUpdatedAt());
        assertFalse(state.hasAttendanceState());
    }

    @Test
    public void representsSavedAndRsvpStateWithServerTimestamp() {
        Instant updatedAt = Instant.parse("2026-08-05T12:00:00Z");
        AttendanceState state = new AttendanceState(
                UUID.randomUUID(), true, RsvpStatus.GOING, updatedAt);

        assertTrue(state.isSaved());
        assertEquals(RsvpStatus.GOING, state.getRsvpStatus());
        assertEquals(updatedAt, state.getUpdatedAt());
        assertTrue(state.hasAttendanceState());
    }

    @Test
    public void rejectsAStateThatExistsWithoutAnUpdateTimestamp() {
        assertThrows(
                NullPointerException.class,
                () -> new AttendanceState(UUID.randomUUID(), true, null, null));
    }

    @Test
    public void rejectsTimestampOnAnEmptyState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AttendanceState(
                        UUID.randomUUID(),
                        false,
                        null,
                        Instant.parse("2026-08-05T12:00:00Z")));
    }
}
