package com.connexa.api.domain.organizer;

/**
 * Raised when publishing is attempted without a durable store behind it.
 *
 * <p>A published event has to outlive a restart. The default non-durable mode therefore
 * refuses to publish rather than creating a listing that would silently disappear.
 */
public class PublicationUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PublicationUnavailableException() {
        super("Publishing requires a durable persistence adapter");
    }
}
