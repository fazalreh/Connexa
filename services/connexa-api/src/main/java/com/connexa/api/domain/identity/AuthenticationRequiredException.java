package com.connexa.api.domain.identity;

/**
 * Raised when the API has no verified identity for a protected operation.
 */
public class AuthenticationRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthenticationRequiredException() {
        super("Authentication is required for this resource.");
    }
}
