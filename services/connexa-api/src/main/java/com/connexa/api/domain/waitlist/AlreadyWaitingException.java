package com.connexa.api.domain.waitlist;

import java.util.UUID;

/** Raised when an attendee who is already queued tries to join again. */
public class AlreadyWaitingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AlreadyWaitingException(UUID eventId) {
        super("Already on the waitlist for event " + eventId);
    }
}
