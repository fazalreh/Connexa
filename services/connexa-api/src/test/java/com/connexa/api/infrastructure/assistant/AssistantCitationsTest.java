package com.connexa.api.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.assistant.AssistantReference;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantCitationsTest {

    private static final EventSummary FIRST = event("Robotics Demo");
    private static final EventSummary SECOND = event("Career Fair");
    private static final List<EventSummary> CONTEXT = List.of(FIRST, SECOND);

    @Test
    @DisplayName("a citation resolves to the event at that position")
    void resolvesCitationToEvent() {
        List<AssistantReference> refs =
                AssistantCitations.resolve("You might like the robotics session [1].", CONTEXT);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).eventId()).isEqualTo(FIRST.id());
        assertThat(refs.get(0).label()).isEqualTo("Robotics Demo");
    }

    @Test
    @DisplayName("citations are returned in the order they appear")
    void preservesCitationOrder() {
        List<AssistantReference> refs =
                AssistantCitations.resolve("First [2], then [1].", CONTEXT);

        assertThat(refs).extracting(AssistantReference::label)
                .containsExactly("Career Fair", "Robotics Demo");
    }

    @Test
    @DisplayName("a repeated citation yields one reference")
    void deduplicatesRepeatedCitations() {
        List<AssistantReference> refs =
                AssistantCitations.resolve("[1] is great. Did I mention [1]?", CONTEXT);

        assertThat(refs).hasSize(1);
    }

    @Test
    @DisplayName("a citation beyond the context is discarded, not guessed")
    void dropsOutOfRangeCitations() {
        // The model inventing "[7]" must never produce a reference to a real event.
        List<AssistantReference> refs =
                AssistantCitations.resolve("See [7] and [1].", CONTEXT);

        assertThat(refs).extracting(AssistantReference::label).containsExactly("Robotics Demo");
    }

    @Test
    @DisplayName("citation zero is discarded")
    void dropsZeroCitation() {
        assertThat(AssistantCitations.resolve("See [0].", CONTEXT)).isEmpty();
    }

    @Test
    @DisplayName("a reply with no citations yields no references")
    void handlesReplyWithoutCitations() {
        assertThat(AssistantCitations.resolve("I could not find a matching event.", CONTEXT))
                .isEmpty();
    }

    @Test
    @DisplayName("citations against an empty context yield nothing")
    void handlesEmptyContext() {
        assertThat(AssistantCitations.resolve("Try [1].", List.of())).isEmpty();
    }

    private static EventSummary event(String title) {
        Instant starts = Instant.parse("2026-07-01T10:00:00Z");
        return new EventSummary(
                UUID.randomUUID(), title, "Summary of " + title, starts, starts.plusSeconds(3600),
                "Asia/Karachi", "Main Auditorium", "Technology", "Connexa Events",
                EventStatus.PUBLISHED, 0L, starts, starts);
    }
}
