package com.connexa.api.domain.checkin;

import java.util.UUID;

/**
 * Raised when a validly-signed pass belongs to someone who does not hold a seat.
 *
 * <p>Happens when an attendee cancelled after their pass was issued. The signature is
 * genuine, so this is reported separately from a bad code: the steward needs to know the
 * person is simply not on the list, not that something is wrong with their phone.
 */
public class NotAttendingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotAttendingException(UUID eventId) {
        super("This guest does not hold a seat for event " + eventId);
    }
}
