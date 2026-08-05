package com.connexa.api.infrastructure.event;

import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import java.util.Optional;
import java.util.UUID;

public interface EventCatalog {

    PageResponse<EventSummary> findPublished(EventQuery query);

    Optional<EventSummary> findById(UUID eventId);
}
