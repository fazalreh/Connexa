package com.connexa.api.infrastructure.checkin;

import com.connexa.api.domain.checkin.NotAttendingException;
import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Refuses check-in while storage is non-durable.
 *
 * <p>An arrival record that vanishes on restart is worse than none: a steward would have no
 * way to tell an unrecorded guest from one whose record was lost.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class UnavailableCheckInStore implements CheckInStore {

    @Override
    public CheckInResult checkIn(IdentityKey identity, UUID eventId, Instant at) {
        throw new NotAttendingException(eventId);
    }

    @Override
    public Optional<Instant> findCheckIn(IdentityKey identity, UUID eventId) {
        return Optional.empty();
    }

    @Override
    public long countCheckedIn(UUID eventId) {
        return 0L;
    }
}
