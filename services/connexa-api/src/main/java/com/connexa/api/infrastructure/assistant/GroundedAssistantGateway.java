package com.connexa.api.infrastructure.assistant;

import com.connexa.api.domain.assistant.AssistantReference;
import com.connexa.api.domain.assistant.AssistantReply;
import com.connexa.api.domain.assistant.AssistantRequest;
import com.connexa.api.domain.assistant.AssistantUnavailableException;
import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.event.EventCatalog;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers questions about the event catalog, grounded in events retrieved for the question.
 *
 * <p>Retrieval runs against the same public catalog boundary the discovery endpoints use, so
 * the assistant can never surface an event a caller could not already see — an unpublished
 * draft cannot leak through a chat reply.
 */
public final class GroundedAssistantGateway implements AssistantGateway {

    private static final Logger log = LoggerFactory.getLogger(GroundedAssistantGateway.class);
    private static final int MAX_REPLY_LENGTH = 4_000;

    private final EventCatalog eventCatalog;
    private final GeminiTextModel textModel;
    private final AssistantRateLimiter rateLimiter;
    private final int maxContextEvents;
    private final Clock clock;

    public GroundedAssistantGateway(
            EventCatalog eventCatalog,
            GeminiTextModel textModel,
            AssistantRateLimiter rateLimiter,
            int maxContextEvents,
            Clock clock) {
        this.eventCatalog = Objects.requireNonNull(eventCatalog, "eventCatalog is required");
        this.textModel = Objects.requireNonNull(textModel, "textModel is required");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter is required");
        this.maxContextEvents = maxContextEvents;
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public AssistantReply answer(VerifiedIdentity identity, AssistantRequest request) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(request, "request is required");

        // Budget is enforced before the call, not after: the point is to not spend it.
        if (!rateLimiter.tryAcquire()) {
            throw new AssistantUnavailableException();
        }

        List<EventSummary> context = retrieve(request);
        Instant now = clock.instant();
        String text;
        try {
            text = textModel.generate(
                    AssistantPrompt.systemInstruction(),
                    AssistantPrompt.userContent(request.message(), context, now));
        } catch (IOException exception) {
            log.warn("Assistant provider call failed", exception);
            throw new AssistantUnavailableException();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssistantUnavailableException();
        }

        if (text.isBlank()) {
            // A blank candidate usually means the response was filtered upstream. Treat it as
            // unavailable rather than returning an empty bubble the user cannot act on.
            throw new AssistantUnavailableException();
        }

        List<AssistantReference> references = AssistantCitations.resolve(text, context);
        return new AssistantReply(
                request.conversationId() == null ? UUID.randomUUID() : request.conversationId(),
                truncate(text),
                references,
                now);
    }

    /**
     * Gathers candidate events: any event the request is explicitly about, then search hits,
     * then upcoming events as a fallback so a vague question still has something to work with.
     */
    private List<EventSummary> retrieve(AssistantRequest request) {
        Map<UUID, EventSummary> found = new LinkedHashMap<>();

        if (request.eventId() != null) {
            eventCatalog.findById(request.eventId())
                    .ifPresent(event -> found.put(event.id(), event));
        }
        addAll(found, eventCatalog.findPublished(
                new EventQuery(request.message(), null, null, 0, maxContextEvents)).items());
        if (found.size() < maxContextEvents) {
            addAll(found, eventCatalog.findPublished(
                    new EventQuery(null, null, null, 0, maxContextEvents)).items());
        }

        List<EventSummary> context = new ArrayList<>(found.values());
        return context.size() <= maxContextEvents
                ? List.copyOf(context)
                : List.copyOf(context.subList(0, maxContextEvents));
    }

    private void addAll(Map<UUID, EventSummary> target, List<EventSummary> events) {
        for (EventSummary event : events) {
            if (target.size() >= maxContextEvents) {
                return;
            }
            target.putIfAbsent(event.id(), event);
        }
    }

    /** Keeps the reply inside the domain limit rather than letting construction fail. */
    private static String truncate(String text) {
        if (text.length() <= MAX_REPLY_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_REPLY_LENGTH - 1).stripTrailing() + "…";
    }
}
