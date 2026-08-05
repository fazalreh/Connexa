package com.connexa.mobile.core.attendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RsvpStatusTest {

    @Test
    public void usesTheApiEnumValueForWrites() {
        assertEquals("GOING", RsvpStatus.GOING.toApiValue());
    }

    @Test
    public void parsesAnApiStatusRegardlessOfPresentationCase() {
        assertEquals(RsvpStatus.INTERESTED, RsvpStatus.fromApiValue(" interested "));
    }

    @Test
    public void rejectsUnknownApiStatuses() {
        assertThrows(IllegalArgumentException.class, () -> RsvpStatus.fromApiValue("WAITLISTED"));
    }
}
