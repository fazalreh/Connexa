package com.connexa.api.domain.waitlist;

import java.util.UUID;

/**
 * Raised when someone tries to queue for an event that still has seats.
 *
 * <p>Joining a queue that is not needed would put an attendee behind a promotion they could
 * have taken directly, so the request is refused rather than quietly accepted.
 */
public class EventNotFullException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EventNotFullException(UUID eventId) {
        super("Event " + eventId + " still has capacity");
    }
}
