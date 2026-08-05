package com.connexa.mobile.core.attendance;

import java.io.IOException;

/** A public-safe error returned by the authenticated attendance API. */
public final class AttendanceApiException extends IOException {

    private final int statusCode;

    public AttendanceApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
