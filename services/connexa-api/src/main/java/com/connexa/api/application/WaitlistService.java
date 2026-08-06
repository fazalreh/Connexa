package com.connexa.api.application;

import com.connexa.api.domain.event.EventCapacity;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.waitlist.EventNotFullException;
import com.connexa.api.domain.waitlist.WaitlistEntry;
import com.connexa.api.infrastructure.attendance.AttendanceStore;
import com.connexa.api.infrastructure.waitlist.WaitlistStore;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lets attendees queue for events that are already full.
 */
@Service
public class WaitlistService {

    private final WaitlistStore waitlistStore;
    private final AttendanceStore attendanceStore;
    private final EventQueryService eventQueryService;

    public WaitlistService(
            WaitlistStore waitlistStore,
            AttendanceStore attendanceStore,
            EventQueryService eventQueryService) {
        this.waitlistStore = Objects.requireNonNull(waitlistStore, "waitlistStore");
        this.attendanceStore = Objects.requireNonNull(attendanceStore, "attendanceStore");
        this.eventQueryService = Objects.requireNonNull(eventQueryService, "eventQueryService");
    }

    /**
     * Joins the queue for a full event.
     *
     * <p>Refused when seats remain: queueing behind a promotion the caller could simply take
     * would leave them worse off than if they had responded directly.
     */
    public WaitlistEntry join(VerifiedIdentity identity, UUID eventId) {
        Objects.requireNonNull(identity, "identity is required");
        eventQueryService.findEvent(eventId);

        EventCapacity capacity = attendanceStore.findCapacity(eventId)
                .orElseThrow(() -> new EventNotFullException(eventId));
        if (!capacity.full()) {
            throw new EventNotFullException(eventId);
        }
        return waitlistStore.join(identity.key(), eventId, Instant.now());
    }

    public boolean leave(VerifiedIdentity identity, UUID eventId) {
        Objects.requireNonNull(identity, "identity is required");
        return waitlistStore.leave(identity.key(), eventId);
    }

    /** The caller's rank in the queue, or empty when they are not waiting. */
    public Optional<Long> placeInQueue(VerifiedIdentity identity, UUID eventId) {
        Objects.requireNonNull(identity, "identity is required");
        return waitlistStore.placeInQueue(identity.key(), eventId);
    }

    public int waitingCount(UUID eventId) {
        return waitlistStore.waiting(eventId).size();
    }
}
