package com.connexa.api.domain.assistant;

import java.util.UUID;

/**
 * A compact event reference returned alongside an assistant answer.
 */
public record AssistantReference(UUID eventId, String label) {

    public AssistantReference {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        label = label.trim();
        if (label.length() > 160) {
            throw new IllegalArgumentException("label must not exceed 160 characters");
        }
    }
}
