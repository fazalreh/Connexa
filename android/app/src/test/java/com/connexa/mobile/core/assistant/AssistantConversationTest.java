package com.connexa.mobile.core.assistant;

import static org.junit.Assert.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class AssistantConversationTest {

    @Test
    public void rejectsAMessageFromAnotherConversation() {
        UUID conversationId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-08-05T12:00:00Z");
        AssistantMessage foreignMessage = new AssistantMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AssistantMessageRole.USER,
                "Show me nearby workshops.",
                List.of(),
                timestamp);

        assertThrows(
                IllegalArgumentException.class,
                () -> new AssistantConversation(
                        conversationId,
                        timestamp,
                        timestamp,
                        List.of(foreignMessage)));
    }

    @Test
    public void rejectsMessagesOutsideTheConversationLifetime() {
        UUID conversationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-05T12:00:00Z");
        AssistantMessage futureMessage = new AssistantMessage(
                UUID.randomUUID(),
                conversationId,
                AssistantMessageRole.USER,
                "Show me nearby workshops.",
                List.of(),
                createdAt.plusSeconds(61));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AssistantConversation(
                        conversationId,
                        createdAt,
                        createdAt.plusSeconds(60),
                        List.of(futureMessage)));
    }
}
