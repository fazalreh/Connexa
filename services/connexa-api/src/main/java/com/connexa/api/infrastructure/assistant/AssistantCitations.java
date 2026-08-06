package com.connexa.api.infrastructure.assistant;

import com.connexa.api.domain.assistant.AssistantReference;
import com.connexa.api.domain.event.EventSummary;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the bracketed citations in a model reply back to real events.
 *
 * <p>References are derived from the numbered context rather than from anything the model
 * writes, so a reference can only ever point at an event that was actually retrieved. A
 * number outside the context range is dropped rather than guessed at — a citation to an
 * event that does not exist is worse than no citation.
 */
public final class AssistantCitations {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    private AssistantCitations() {
    }

    public static List<AssistantReference> resolve(String reply, List<EventSummary> context) {
        Objects.requireNonNull(reply, "reply is required");
        Objects.requireNonNull(context, "context is required");

        Set<Integer> cited = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(reply);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            if (number >= 1 && number <= context.size()) {
                cited.add(number);
            }
        }

        List<AssistantReference> references = new ArrayList<>(cited.size());
        for (int number : cited) {
            EventSummary event = context.get(number - 1);
            references.add(new AssistantReference(event.id(), event.title()));
        }
        return List.copyOf(references);
    }
}
