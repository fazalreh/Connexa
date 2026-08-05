package com.connexa.mobile.core.network;

import java.io.IOException;

/**
 * A non-sensitive representation of an unsuccessful event API response.
 */
public final class EventApiException extends IOException {

    private final int statusCode;

    public EventApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
