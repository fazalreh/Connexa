package com.connexa.mobile.core.attendance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable saved-event and RSVP state for the signed-in attendee and one event.
 */
public final class AttendanceState {

    private final UUID eventId;
    private final boolean saved;
    private final RsvpStatus rsvpStatus;
    private final Instant updatedAt;

    /**
     * @param rsvpStatus the attendee's response, or {@code null} when no RSVP has been set
     * @param updatedAt the server timestamp for a state that exists, otherwise {@code null}
     */
    public AttendanceState(UUID eventId, boolean saved, RsvpStatus rsvpStatus, Instant updatedAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId is required");
        this.saved = saved;
        this.rsvpStatus = rsvpStatus;
        if (saved || rsvpStatus != null) {
            this.updatedAt = Objects.requireNonNull(
                    updatedAt,
                    "updatedAt is required when attendance state exists");
        } else if (updatedAt != null) {
            throw new IllegalArgumentException("updatedAt must be absent when attendance state is empty");
        } else {
            this.updatedAt = null;
        }
    }

    public static AttendanceState empty(UUID eventId) {
        return new AttendanceState(eventId, false, null, null);
    }

    public UUID getEventId() {
        return eventId;
    }

    public boolean isSaved() {
        return saved;
    }

    /** Returns the current RSVP response, or {@code null} when none has been set. */
    public RsvpStatus getRsvpStatus() {
        return rsvpStatus;
    }

    /** Returns the server timestamp, or {@code null} for an empty state. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasAttendanceState() {
        return saved || rsvpStatus != null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttendanceState)) {
            return false;
        }
        AttendanceState that = (AttendanceState) other;
        return saved == that.saved
                && eventId.equals(that.eventId)
                && rsvpStatus == that.rsvpStatus
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, saved, rsvpStatus, updatedAt);
    }
}
