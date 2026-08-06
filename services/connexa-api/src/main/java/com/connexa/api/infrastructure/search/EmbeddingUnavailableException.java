package com.connexa.api.infrastructure.search;

/** Raised when the embedding provider could not be reached or refused the request. */
public class EmbeddingUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
