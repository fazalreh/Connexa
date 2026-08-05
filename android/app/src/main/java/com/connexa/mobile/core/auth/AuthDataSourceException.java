package com.connexa.mobile.core.auth;

import java.util.Objects;

/**
 * A provider-independent authentication failure that can be mapped to safe user feedback.
 */
public final class AuthDataSourceException extends Exception {

    public enum Reason {
        INVALID_CREDENTIALS,
        ACCOUNT_ALREADY_EXISTS,
        SESSION_EXPIRED,
        TEMPORARILY_UNAVAILABLE,
        UNKNOWN
    }

    private final Reason reason;

    public AuthDataSourceException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public AuthDataSourceException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason getReason() {
        return reason;
    }
}
