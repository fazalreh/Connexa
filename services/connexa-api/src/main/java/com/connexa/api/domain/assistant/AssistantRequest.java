package com.connexa.api.domain.assistant;

import java.util.UUID;

/** Validated, identity-bound input for the server-side assistant boundary. */
public record AssistantRequest(String message, UUID conversationId, UUID eventId) {

    private static final int MAX_MESSAGE_LENGTH = 1_000;

    public AssistantRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        message = message.trim();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
    }
}
