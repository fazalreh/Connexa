package com.connexa.api.infrastructure.publication;

import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.organizer.DraftAlreadyPublishedException;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for turning a private draft into a public event.
 */
public interface EventPublicationStore {

    /**
     * Writes the public event and records the link back to its draft in one unit of work.
     *
     * <p>The two must not be separable. Writing the event without the link would leave the
     * draft publishable a second time, producing a duplicate listing that nothing points at.
     *
     * @param totalCapacity declared seat limit, or null for an unbounded event
     * @throws DraftAlreadyPublishedException if the draft has already produced an event
     */
    EventSummary publish(UUID draftId, EventSummary event, Integer totalCapacity);

    /**
     * The event a draft produced, or empty when it has not been published.
     */
    Optional<UUID> findPublishedEventId(UUID draftId);
}
