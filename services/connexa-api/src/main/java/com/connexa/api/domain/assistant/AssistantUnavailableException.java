package com.connexa.api.domain.assistant;

/**
 * Raised when no approved server-side assistant provider is available.
 */
public class AssistantUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantUnavailableException() {
        super("The assistant is not available yet.");
    }
}
