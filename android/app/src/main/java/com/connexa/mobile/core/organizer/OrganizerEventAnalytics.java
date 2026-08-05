package com.connexa.mobile.core.organizer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable organizer-facing attendance and capacity snapshot for one event. */
public final class OrganizerEventAnalytics {

    private final UUID eventId;
    private final String eventTitle;
    private final int capacity;
    private final int registeredCount;
    private final int checkedInCount;
    private final int waitlistedCount;
    private final Instant observedAt;

    public OrganizerEventAnalytics(
            UUID eventId,
            String eventTitle,
            int capacity,
            int registeredCount,
            int checkedInCount,
            int waitlistedCount,
            Instant observedAt) {
        this.eventId = Objects.requireNonNull(eventId, "eventId is required");
        this.eventTitle = requireText(eventTitle, "eventTitle");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least one");
        }
        if (registeredCount < 0 || registeredCount > capacity) {
            throw new IllegalArgumentException("registeredCount must be between zero and capacity");
        }
        if (checkedInCount < 0 || checkedInCount > registeredCount) {
            throw new IllegalArgumentException(
                    "checkedInCount must be between zero and registeredCount");
        }
        if (waitlistedCount < 0) {
            throw new IllegalArgumentException("waitlistedCount must be zero or greater");
        }
        this.capacity = capacity;
        this.registeredCount = registeredCount;
        this.checkedInCount = checkedInCount;
        this.waitlistedCount = waitlistedCount;
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt is required");
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRegisteredCount() {
        return registeredCount;
    }

    public int getCheckedInCount() {
        return checkedInCount;
    }

    public int getWaitlistedCount() {
        return waitlistedCount;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
