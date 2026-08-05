package com.connexa.api.domain.attendance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-identity saved and RSVP state for one event.
 */
public record AttendanceState(
        UUID eventId,
        boolean saved,
        RsvpStatus rsvpStatus,
        Instant updatedAt) {

    public AttendanceState {
        Objects.requireNonNull(eventId, "eventId is required");
        if (saved || rsvpStatus != null) {
            Objects.requireNonNull(updatedAt, "updatedAt is required when attendance state exists");
        } else {
            updatedAt = null;
        }
    }

    public boolean hasState() {
        return saved || rsvpStatus != null;
    }

    public static AttendanceState empty(UUID eventId) {
        return new AttendanceState(eventId, false, null, null);
    }
}
