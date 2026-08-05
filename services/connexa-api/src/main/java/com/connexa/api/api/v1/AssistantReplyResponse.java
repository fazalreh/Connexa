package com.connexa.api.api.v1;

import com.connexa.api.domain.assistant.AssistantReference;
import com.connexa.api.domain.assistant.AssistantReply;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssistantReplyResponse(
        UUID conversationId,
        String message,
        List<AssistantReference> references,
        Instant createdAt) {

    public static AssistantReplyResponse from(AssistantReply reply) {
        return new AssistantReplyResponse(
                reply.conversationId(),
                reply.message(),
                reply.references(),
                reply.createdAt());
    }
}
