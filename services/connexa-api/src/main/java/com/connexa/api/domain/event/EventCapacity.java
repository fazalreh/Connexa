package com.connexa.api.domain.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Seat availability for one event.
 *
 * <p>A null {@code totalCapacity} means the event is unbounded and every reservation
 * succeeds.
 *
 * <p>The record deliberately tolerates {@code reserved > totalCapacity} rather than
 * rejecting it. An organizer lowering capacity below the number of seats already taken
 * produces exactly that state, and a read model that threw on it would make those events
 * unreadable instead of merely full.
 */
public record EventCapacity(UUID eventId, Integer totalCapacity, int reserved) {

    public EventCapacity {
        Objects.requireNonNull(eventId, "eventId is required");
        if (totalCapacity != null && totalCapacity < 0) {
            throw new IllegalArgumentException("totalCapacity must be zero or greater");
        }
        if (reserved < 0) {
            throw new IllegalArgumentException("reserved must be zero or greater");
        }
    }

    public static EventCapacity unlimited(UUID eventId) {
        return new EventCapacity(eventId, null, 0);
    }

    public boolean unbounded() {
        return totalCapacity == null;
    }

    /** Remaining seats, or null when the event is unbounded. Never negative. */
    public Integer spotsRemaining() {
        if (unbounded()) {
            return null;
        }
        return Math.max(0, totalCapacity - reserved);
    }

    public boolean full() {
        return !unbounded() && reserved >= totalCapacity;
    }
}
