package com.connexa.api.infrastructure.event;

import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Safe local catalog used until a configured persistence adapter is introduced.
 * It deliberately contains no production data and never contacts external services.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class InMemoryEventCatalog implements EventCatalog {

    @Override
    public PageResponse<EventSummary> findPublished(EventQuery query) {
        return PageResponse.empty(query.page(), query.size());
    }

    @Override
    public Optional<EventSummary> findById(UUID eventId) {
        return Optional.empty();
    }
}
