package com.connexa.api.domain.identity;

/**
 * Raised when an authenticated identity lacks the required role.
 */
public class ActorNotAuthorizedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ActorNotAuthorizedException(String action) {
        super("You are not authorized to " + action + ".");
    }
}
