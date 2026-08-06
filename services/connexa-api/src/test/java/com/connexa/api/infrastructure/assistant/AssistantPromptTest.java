package com.connexa.api.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantPromptTest {

    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    @Test
    @DisplayName("the instruction forbids inventing events and requires citations")
    void instructionEstablishesGrounding() {
        String instruction = AssistantPrompt.systemInstruction();

        assertThat(instruction).contains("Never invent an event");
        assertThat(instruction).contains("cite it with its bracketed");
    }

    @Test
    @DisplayName("context events are numbered from one so citations can be resolved")
    void numbersContextFromOne() {
        String content = AssistantPrompt.userContent(
                "any robotics events?", List.of(event("Robotics Demo"), event("Career Fair")), NOW);

        assertThat(content).contains("[1] Robotics Demo");
        assertThat(content).contains("[2] Career Fair");
    }

    @Test
    @DisplayName("each event carries the details an answer would need")
    void includesEventDetails() {
        String content = AssistantPrompt.userContent("what is on?", List.of(event("Robotics Demo")), NOW);

        assertThat(content).contains("when:").contains("where: Main Auditorium")
                .contains("category: Technology").contains("organiser: Connexa Events")
                .contains("about: Summary of Robotics Demo");
    }

    @Test
    @DisplayName("event times are rendered in the event's own zone")
    void rendersTimesInTheEventZone() {
        // 10:00 UTC is 15:00 in Asia/Karachi; showing UTC would misinform the attendee.
        String content = AssistantPrompt.userContent("when?", List.of(event("Robotics Demo")), NOW);

        assertThat(content).contains("15:00").contains("Asia/Karachi");
    }

    @Test
    @DisplayName("an empty context says so rather than leaving the model to guess")
    void statesWhenContextIsEmpty() {
        String content = AssistantPrompt.userContent("anything?", List.of(), NOW);

        assertThat(content).contains("(no events matched this query)");
    }

    @Test
    @DisplayName("the question is included verbatim")
    void includesTheQuestion() {
        String content = AssistantPrompt.userContent("any robotics events?", List.of(), NOW);

        assertThat(content).contains("QUESTION").contains("any robotics events?");
    }

    @Test
    @DisplayName("the current time is supplied so relative questions can be answered")
    void includesCurrentTime() {
        assertThat(AssistantPrompt.userContent("what is on today?", List.of(), NOW))
                .contains("CURRENT TIME").contains("2026");
    }

    private static EventSummary event(String title) {
        Instant starts = Instant.parse("2026-07-01T10:00:00Z");
        return new EventSummary(
                UUID.randomUUID(), title, "Summary of " + title, starts, starts.plusSeconds(3600),
                "Asia/Karachi", "Main Auditorium", "Technology", "Connexa Events",
                EventStatus.PUBLISHED, 0L, starts, starts);
    }
}
