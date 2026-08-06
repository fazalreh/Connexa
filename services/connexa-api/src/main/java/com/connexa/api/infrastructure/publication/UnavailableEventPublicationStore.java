package com.connexa.api.infrastructure.publication;

import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.organizer.PublicationUnavailableException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Refuses to publish while the service is running on non-durable storage.
 *
 * <p>The in-memory mode loses everything on restart. Accepting a publish here would report
 * success for a public listing that vanishes with the process, so this adapter fails closed
 * instead, matching how the other default adapters behave.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class UnavailableEventPublicationStore implements EventPublicationStore {

    @Override
    public EventSummary publish(UUID draftId, EventSummary event, Integer totalCapacity) {
        throw new PublicationUnavailableException();
    }

    @Override
    public Optional<UUID> findPublishedEventId(UUID draftId) {
        return Optional.empty();
    }
}
