package com.connexa.api.api.v1;

import com.connexa.api.domain.assistant.AssistantRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssistantMessageRequest(
        @NotBlank @Size(max = 1_000) String message,
        UUID conversationId,
        UUID eventId) {

    public AssistantRequest toAssistantRequest() {
        return new AssistantRequest(message, conversationId, eventId);
    }
}
