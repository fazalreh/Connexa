package com.connexa.api.domain.media;

/**
 * Raised when a file the organizer chose is not something this service will publish.
 */
public class UnsupportedMediaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedMediaException(String message) {
        super(message);
    }
}
