package com.connexa.mobile.core.organizer;

/** A publish attempt that failed, carrying a message safe to show an organizer. */
public final class OrganizerApiException extends Exception {

    public OrganizerApiException(String message) {
        super(message);
    }

    public OrganizerApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
