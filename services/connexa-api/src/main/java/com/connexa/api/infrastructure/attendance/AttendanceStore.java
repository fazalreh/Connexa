package com.connexa.api.infrastructure.attendance;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.EventAtCapacityException;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventCapacity;
import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for a caller's saved-event and RSVP state.
 */
public interface AttendanceStore {

    Optional<AttendanceState> find(IdentityKey identity, UUID eventId);

    List<AttendanceState> findAll(IdentityKey identity);

    AttendanceState setSaved(IdentityKey identity, UUID eventId, boolean saved, Instant updatedAt);

    /**
     * Records an RSVP and adjusts the event's reserved seat count to match, in one unit of
     * work.
     *
     * <p>Seats are tied to {@link RsvpStatus#GOING} only. Moving into GOING takes a seat,
     * moving out of it releases one, and repeating the same status changes nothing — so a
     * retried request cannot consume a second seat.
     *
     * <p>Reserving and recording must not be separable. Split across two calls, a failure
     * between them would strand a seat that no attendee holds and no release would ever
     * return.
     *
     * @throws EventAtCapacityException when a seat is needed and none remain
     */
    AttendanceState setRsvp(IdentityKey identity, UUID eventId, RsvpStatus rsvpStatus, Instant updatedAt);

    /**
     * Current seat availability, or empty when the event is unknown to this store.
     */
    Optional<EventCapacity> findCapacity(UUID eventId);
}
