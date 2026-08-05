package com.connexa.mobile.core.attendance;

import java.util.Locale;

/** The response an attendee may hold for an event invitation. */
public enum RsvpStatus {
    GOING,
    INTERESTED,
    DECLINED;

    /** Returns the value used by the Connexa API. */
    public String toApiValue() {
        return name();
    }

    /** Parses a response value received from the Connexa API. */
    public static RsvpStatus fromApiValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("RSVP status is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported RSVP status", exception);
        }
    }
}
