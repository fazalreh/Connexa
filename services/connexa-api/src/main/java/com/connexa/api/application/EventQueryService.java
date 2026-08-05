package com.connexa.api.application;

import com.connexa.api.domain.event.EventNotFoundException;
import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.infrastructure.event.EventCatalog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventQueryService {

    private final EventCatalog eventCatalog;

    public EventQueryService(EventCatalog eventCatalog) {
        this.eventCatalog = eventCatalog;
    }

    public PageResponse<EventSummary> findEvents(String query, Instant from, Instant to, int page, int size) {
        return eventCatalog.findPublished(new EventQuery(query, from, to, page, size));
    }

    public EventSummary findEvent(UUID eventId) {
        return eventCatalog.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
