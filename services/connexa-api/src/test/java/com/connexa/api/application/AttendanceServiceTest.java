package com.connexa.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.attendance.InMemoryAttendanceStore;
import com.connexa.api.infrastructure.event.EventCatalog;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceServiceTest {

    @Test
    void keepsSavedAndRsvpStateScopedToTheVerifiedIdentity() {
        UUID eventId = UUID.randomUUID();
        AttendanceService service = new AttendanceService(
                new InMemoryAttendanceStore(),
                new EventQueryService(catalogFor(eventId)));
        VerifiedIdentity identity = attendeeIdentity();

        AttendanceState saved = service.save(identity, eventId);
        assertTrue(saved.saved());
        assertNull(saved.rsvpStatus());

        AttendanceState attending = service.setRsvp(identity, eventId, RsvpStatus.GOING);
        assertTrue(attending.saved());
        assertEquals(RsvpStatus.GOING, attending.rsvpStatus());

        AttendanceState unsaved = service.unsave(identity, eventId);
        assertFalse(unsaved.saved());
        assertEquals(RsvpStatus.GOING, unsaved.rsvpStatus());

        AttendanceState cleared = service.clearRsvp(identity, eventId);
        assertFalse(cleared.hasState());
        assertNull(cleared.updatedAt());
        assertEquals(0L, service.findAll(identity, 0, 20).total());
    }

    private static VerifiedIdentity attendeeIdentity() {
        return new VerifiedIdentity(
                new IdentityKey("test-issuer", "attendee-1"),
                "Attendee",
                "attendee@example.test",
                Set.of(IdentityRole.ATTENDEE));
    }

    private static EventCatalog catalogFor(UUID eventId) {
        EventSummary event = new EventSummary(
                eventId,
                "Community meetup",
                "A verified test event",
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-05T11:00:00Z"),
                "Asia/Karachi",
                "Main hall",
                "Community",
                "Connexa",
                EventStatus.PUBLISHED,
                0,
                Instant.parse("2026-08-01T10:00:00Z"),
                Instant.parse("2026-08-01T10:00:00Z"));
        return new EventCatalog() {
            @Override
            public PageResponse<EventSummary> findPublished(EventQuery query) {
                return PageResponse.empty(query.page(), query.size());
            }

            @Override
            public Optional<EventSummary> findById(UUID id) {
                return eventId.equals(id) ? Optional.of(event) : Optional.empty();
            }
        };
    }
}
