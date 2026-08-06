package com.connexa.api.domain.waitlist;

import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One attendee's place in an event's queue.
 *
 * <p>Position is a monotonic number rather than a timestamp. Two people can join in the same
 * millisecond, and wall-clock time can move backwards, so neither gives the total order that
 * fairness depends on.
 *
 * @param promotedAt set once a seat has been handed to this entry; null while still waiting
 */
public record WaitlistEntry(
        UUID eventId,
        IdentityKey identity,
        long position,
        Instant joinedAt,
        Instant promotedAt) {

    public WaitlistEntry {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(joinedAt, "joinedAt is required");
        if (position < 1) {
            throw new IllegalArgumentException("position must be at least 1");
        }
        if (promotedAt != null && promotedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("promotedAt cannot be before joinedAt");
        }
    }

    public boolean waiting() {
        return promotedAt == null;
    }
}
