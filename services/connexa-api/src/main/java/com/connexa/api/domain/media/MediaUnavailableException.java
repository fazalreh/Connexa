package com.connexa.api.domain.media;

/**
 * Raised when no image storage is configured for this service.
 */
public class MediaUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MediaUnavailableException() {
        super("Image uploads are not available yet.");
    }
}
