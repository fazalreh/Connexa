package com.connexa.mobile.core.attendance;

import java.util.Objects;
import java.util.UUID;

/**
 * Seat availability for one event.
 *
 * <p>A null {@code totalCapacity} means unbounded, which is why "no limit" and "no seats
 * left" must never be collapsed into the same value: they lead to opposite interfaces.
 */
public final class EventCapacity {

    private final UUID eventId;
    private final Integer totalCapacity;
    private final Integer spotsRemaining;
    private final int reserved;
    private final boolean full;

    public EventCapacity(
            UUID eventId, Integer totalCapacity, Integer spotsRemaining, int reserved, boolean full) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.totalCapacity = totalCapacity;
        this.spotsRemaining = spotsRemaining;
        this.reserved = reserved;
        this.full = full;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Integer getTotalCapacity() {
        return totalCapacity;
    }

    public Integer getSpotsRemaining() {
        return spotsRemaining;
    }

    public int getReserved() {
        return reserved;
    }

    public boolean isFull() {
        return full;
    }

    public boolean isUnbounded() {
        return totalCapacity == null;
    }
}
