package com.connexa.api.domain.assistant;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral assistant reply shape.
 */
public record AssistantReply(
        UUID conversationId,
        String message,
        List<AssistantReference> references,
        Instant createdAt) {

    public AssistantReply {
        Objects.requireNonNull(conversationId, "conversationId is required");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        message = message.trim();
        if (message.length() > 4_000) {
            throw new IllegalArgumentException("message must not exceed 4000 characters");
        }
        references = List.copyOf(Objects.requireNonNull(references, "references are required"));
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}
