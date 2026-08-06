package com.connexa.mobile.core.checkin;

import java.io.IOException;

/**
 * A pass the door refused, carrying a message fit to read aloud to the person holding it.
 */
public class CheckInException extends IOException {

    private static final long serialVersionUID = 1L;

    public CheckInException(String message) {
        super(message);
    }
}
