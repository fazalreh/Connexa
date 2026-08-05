package com.connexa.mobile.core.assistant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, ordered conversation state supplied by the Connexa backend.
 */
public final class AssistantConversation {

    public static final int MAX_MESSAGE_COUNT = 200;

    private final UUID id;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<AssistantMessage> messages;

    public AssistantConversation(
            UUID id,
            Instant createdAt,
            Instant updatedAt,
            List<AssistantMessage> messages) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (this.updatedAt.isBefore(this.createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        this.messages = immutableCopy(messages);
        if (this.messages.size() > MAX_MESSAGE_COUNT) {
            throw new IllegalArgumentException("message count exceeds the allowed limit");
        }
        validateMessages();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<AssistantMessage> getMessages() {
        return messages;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssistantConversation)) {
            return false;
        }
        AssistantConversation that = (AssistantConversation) other;
        return id.equals(that.id)
                && createdAt.equals(that.createdAt)
                && updatedAt.equals(that.updatedAt)
                && messages.equals(that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt, updatedAt, messages);
    }

    private void validateMessages() {
        Instant previousCreatedAt = createdAt;
        for (AssistantMessage message : messages) {
            if (!id.equals(message.getConversationId())) {
                throw new IllegalArgumentException("every message must belong to this conversation");
            }
            if (message.getCreatedAt().isBefore(createdAt)
                    || message.getCreatedAt().isAfter(updatedAt)) {
                throw new IllegalArgumentException("message time must fall within the conversation lifetime");
            }
            if (message.getCreatedAt().isBefore(previousCreatedAt)) {
                throw new IllegalArgumentException("messages must be ordered by creation time");
            }
            previousCreatedAt = message.getCreatedAt();
        }
    }

    private static List<AssistantMessage> immutableCopy(List<AssistantMessage> source) {
        Objects.requireNonNull(source, "messages are required");
        List<AssistantMessage> copy = new ArrayList<>(source.size());
        for (AssistantMessage message : source) {
            copy.add(Objects.requireNonNull(message, "messages cannot contain null values"));
        }
        return Collections.unmodifiableList(copy);
    }
}
