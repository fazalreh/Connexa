package com.connexa.api.infrastructure.waitlist;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.waitlist.WaitlistEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for event waitlists. */
public interface WaitlistStore extends WaitlistPromoter {

    WaitlistEntry join(IdentityKey identity, UUID eventId, Instant joinedAt);

    boolean leave(IdentityKey identity, UUID eventId);

    Optional<WaitlistEntry> find(IdentityKey identity, UUID eventId);

    /** Rank among those still waiting, rather than the raw stored position. */
    Optional<Long> placeInQueue(IdentityKey identity, UUID eventId);

    List<WaitlistEntry> waiting(UUID eventId);
}
