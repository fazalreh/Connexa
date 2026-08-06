package com.connexa.api.infrastructure.waitlist;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.PublicationUnavailableException;
import com.connexa.api.domain.waitlist.WaitlistEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Refuses queueing while storage is non-durable.
 *
 * <p>A queue whose order is lost on restart is worse than no queue: it would promise
 * fairness it cannot keep.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class UnavailableWaitlistStore implements WaitlistStore {

    @Override
    public WaitlistEntry join(IdentityKey identity, UUID eventId, Instant joinedAt) {
        throw new PublicationUnavailableException();
    }

    @Override
    public boolean leave(IdentityKey identity, UUID eventId) {
        return false;
    }

    @Override
    public Optional<WaitlistEntry> find(IdentityKey identity, UUID eventId) {
        return Optional.empty();
    }

    @Override
    public Optional<Long> placeInQueue(IdentityKey identity, UUID eventId) {
        return Optional.empty();
    }

    @Override
    public List<WaitlistEntry> waiting(UUID eventId) {
        return List.of();
    }

    @Override
    public boolean promoteNext(UUID eventId, Instant at) {
        return false;
    }
}
