package com.connexa.api.infrastructure.checkin;

import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for door check-in. */
public interface CheckInStore {

    /**
     * Marks an attendee as arrived, once.
     *
     * <p>Returns the time already recorded when they were checked in before. A second scan is
     * a normal occurrence at a busy door, so it reports the original arrival rather than
     * failing or overwriting it.
     *
     * @return the recorded arrival time, and whether this call was the one that set it
     */
    CheckInResult checkIn(IdentityKey identity, UUID eventId, Instant at);

    /** Arrival time for an attendee, or empty if they have not been checked in. */
    Optional<Instant> findCheckIn(IdentityKey identity, UUID eventId);

    /** How many attendees have arrived, for the organizer's door view. */
    long countCheckedIn(UUID eventId);
}
