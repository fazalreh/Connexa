package com.connexa.mobile.core.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class AssistantMessageTest {

    @Test
    public void storesDefensiveImmutableReferences() {
        UUID conversationId = UUID.randomUUID();
        AssistantReference reference = new AssistantReference(
                UUID.randomUUID(), "Design workshop", "Starts at 3:00 PM.");
        List<AssistantReference> suppliedReferences = new ArrayList<>(List.of(reference));

        AssistantMessage message = new AssistantMessage(
                UUID.randomUUID(),
                conversationId,
                AssistantMessageRole.ASSISTANT,
                "The design workshop is open for registration.",
                suppliedReferences,
                Instant.parse("2026-08-05T12:00:00Z"));

        suppliedReferences.clear();

        assertEquals(List.of(reference), message.getReferences());
        assertThrows(
                UnsupportedOperationException.class,
                () -> message.getReferences().add(reference));
    }

    @Test
    public void rejectsReferencesOnUserMessages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssistantMessage(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        AssistantMessageRole.USER,
                        "Can I attend?",
                        List.of(new AssistantReference(
                                UUID.randomUUID(), "Design workshop", "Starts at 3:00 PM.")),
                        Instant.parse("2026-08-05T12:00:00Z")));
    }
}
