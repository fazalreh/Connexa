package com.connexa.api.infrastructure.attendance;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
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

    AttendanceState setRsvp(IdentityKey identity, UUID eventId, RsvpStatus rsvpStatus, Instant updatedAt);
}
