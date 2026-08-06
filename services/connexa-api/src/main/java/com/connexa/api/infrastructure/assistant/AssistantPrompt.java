package com.connexa.api.infrastructure.assistant;

import com.connexa.api.domain.event.EventSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the instruction and context sent to the model.
 *
 * <p>The assistant is grounded: it may only describe events supplied here. A model left free
 * to invent plausible-sounding events would produce listings with no id behind them, which
 * users would then try to open. Numbering the context lets the reply cite an event by index,
 * so a citation can be resolved to a real id instead of trusting the model to reproduce a
 * UUID correctly.
 */
public final class AssistantPrompt {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);

    private AssistantPrompt() {
    }

    public static String systemInstruction() {
        return """
                You are the Connexa event assistant. You help attendees find and understand \
                events on the Connexa platform.

                Rules you must follow:
                1. Only describe events listed in the CONTEXT section of the user message. \
                Never invent an event, a date, a venue, or an organiser.
                2. When you refer to an event from the context, cite it with its bracketed \
                number, for example [2]. Cite every event you mention.
                3. If the context contains nothing relevant, say plainly that you could not \
                find a matching event and suggest the user broaden their search. Do not \
                speculate.
                4. Answer questions unrelated to events by saying that you can only help with \
                Connexa events.
                5. Be concise. Two or three short sentences is usually enough. Do not use \
                markdown headings or bullet characters.
                """;
    }

    /**
     * Renders the retrieved events plus the user's question into a single user turn.
     *
     * @param events retrieval results, in the order their citation numbers refer to
     */
    public static String userContent(String message, List<EventSummary> events, Instant now) {
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(events, "events are required");
        Objects.requireNonNull(now, "now is required");

        StringBuilder prompt = new StringBuilder("CONTEXT\n");
        if (events.isEmpty()) {
            prompt.append("(no events matched this query)\n");
        } else {
            for (int index = 0; index < events.size(); index++) {
                EventSummary event = events.get(index);
                ZoneId zone = ZoneId.of(event.timeZone());
                prompt.append('[').append(index + 1).append("] ").append(event.title()).append('\n')
                        .append("    when: ").append(WHEN.format(event.startsAt().atZone(zone)))
                        .append(" (").append(event.timeZone()).append(")\n")
                        .append("    where: ").append(event.venueName()).append('\n')
                        .append("    category: ").append(event.category()).append('\n')
                        .append("    organiser: ").append(event.organizerName()).append('\n')
                        .append("    about: ").append(event.summary()).append('\n');
            }
        }
        return prompt.append("\nCURRENT TIME: ").append(WHEN.format(now.atZone(ZoneId.of("UTC"))))
                .append(" (UTC)\n\nQUESTION\n").append(message).toString();
    }
}
