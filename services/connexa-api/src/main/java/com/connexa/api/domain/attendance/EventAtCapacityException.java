package com.connexa.api.domain.attendance;

import java.util.UUID;

/**
 * Raised when an attendee asks for a seat on an event that has none left.
 *
 * <p>This is a conflict with the current state of the event rather than a malformed
 * request: the same call would succeed once a seat is released.
 */
public class EventAtCapacityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID eventId;

    public EventAtCapacityException(UUID eventId) {
        super("Event " + eventId + " has no remaining capacity");
        this.eventId = eventId;
    }

    public UUID eventId() {
        return eventId;
    }
}
