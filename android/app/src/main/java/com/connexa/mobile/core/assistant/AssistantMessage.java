package com.connexa.mobile.core.assistant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable message returned by the Connexa assistant API.
 */
public final class AssistantMessage {

    public static final int MAX_CONTENT_LENGTH = 4_000;

    private final UUID id;
    private final UUID conversationId;
    private final AssistantMessageRole role;
    private final String content;
    private final List<AssistantReference> references;
    private final Instant createdAt;

    public AssistantMessage(
            UUID id,
            UUID conversationId,
            AssistantMessageRole role,
            String content,
            List<AssistantReference> references,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId is required");
        this.role = Objects.requireNonNull(role, "role is required");
        this.content = requireContent(content);
        this.references = immutableCopy(references, "references");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");

        if (role == AssistantMessageRole.USER && !this.references.isEmpty()) {
            throw new IllegalArgumentException("user messages cannot include event references");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public AssistantMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public List<AssistantReference> getReferences() {
        return references;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssistantMessage)) {
            return false;
        }
        AssistantMessage that = (AssistantMessage) other;
        return id.equals(that.id)
                && conversationId.equals(that.conversationId)
                && role == that.role
                && content.equals(that.content)
                && references.equals(that.references)
                && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, conversationId, role, content, references, createdAt);
    }

    private static String requireContent(String value) {
        if (value == null) {
            throw new IllegalArgumentException("content is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("content is required");
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content exceeds the allowed length");
        }
        return normalized;
    }

    private static List<AssistantReference> immutableCopy(
            List<AssistantReference> source, String field) {
        Objects.requireNonNull(source, field + " are required");
        List<AssistantReference> copy = new ArrayList<>(source.size());
        for (AssistantReference reference : source) {
            copy.add(Objects.requireNonNull(reference, field + " cannot contain null values"));
        }
        return Collections.unmodifiableList(copy);
    }
}
