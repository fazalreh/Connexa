package com.connexa.api.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connexa.api.domain.event.EventNotFoundException;
import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.infrastructure.event.EventCatalog;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventQueryServiceTest {

    @Test
    void doesNotExposeAnUnpublishedEventByIdentifier() {
        UUID eventId = UUID.randomUUID();
        EventSummary draft = event(eventId, EventStatus.DRAFT);
        EventCatalog catalog = new EventCatalog() {
            @Override
            public PageResponse<EventSummary> findPublished(EventQuery query) {
                return PageResponse.empty(query.page(), query.size());
            }

            @Override
            public Optional<EventSummary> findById(UUID id) {
                return eventId.equals(id) ? Optional.of(draft) : Optional.empty();
            }
        };

        EventQueryService service = new EventQueryService(catalog);

        assertThrows(EventNotFoundException.class, () -> service.findEvent(eventId));
    }

    private static EventSummary event(UUID eventId, EventStatus status) {
        Instant createdAt = Instant.parse("2026-08-05T10:00:00Z");
        return new EventSummary(
                eventId,
                "Community meetup",
                "A private event draft for service testing.",
                createdAt.plusSeconds(3_600),
                createdAt.plusSeconds(7_200),
                "Asia/Karachi",
                "Main hall",
                "Community",
                "Connexa",
                status,
                0,
                createdAt,
                createdAt);
    }
}
