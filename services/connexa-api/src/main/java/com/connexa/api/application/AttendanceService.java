package com.connexa.api.application;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventCapacity;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.attendance.AttendanceStore;
import com.connexa.api.infrastructure.realtime.CapacityBroadcaster;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Coordinates identity-scoped saved-event and RSVP changes.
 */
@Service
public class AttendanceService {

    private final AttendanceStore attendanceStore;
    private final EventQueryService eventQueryService;
    private final CapacityBroadcaster capacityBroadcaster;

    public AttendanceService(
            AttendanceStore attendanceStore,
            EventQueryService eventQueryService,
            CapacityBroadcaster capacityBroadcaster) {
        this.attendanceStore = Objects.requireNonNull(attendanceStore, "attendanceStore is required");
        this.eventQueryService = Objects.requireNonNull(eventQueryService, "eventQueryService is required");
        this.capacityBroadcaster = Objects.requireNonNull(capacityBroadcaster, "capacityBroadcaster");
    }

    public AttendanceState find(VerifiedIdentity identity, UUID eventId) {
        requireExistingEvent(eventId);
        return attendanceStore.find(identity.key(), eventId).orElseGet(() -> AttendanceState.empty(eventId));
    }

    public PageResponse<AttendanceState> findAll(VerifiedIdentity identity, int page, int size) {
        validatePage(page, size);
        List<AttendanceState> all = attendanceStore.findAll(identity.key());
        long total = all.size();
        long start = (long) page * size;
        if (start >= total) {
            return new PageResponse<>(List.of(), page, size, total);
        }
        int fromIndex = (int) start;
        int toIndex = Math.min(fromIndex + size, all.size());
        return new PageResponse<>(all.subList(fromIndex, toIndex), page, size, total);
    }

    public AttendanceState save(VerifiedIdentity identity, UUID eventId) {
        requireExistingEvent(eventId);
        return attendanceStore.setSaved(identity.key(), eventId, true, Instant.now());
    }

    public AttendanceState unsave(VerifiedIdentity identity, UUID eventId) {
        requireExistingEvent(eventId);
        return attendanceStore.setSaved(identity.key(), eventId, false, Instant.now());
    }

    public AttendanceState setRsvp(VerifiedIdentity identity, UUID eventId, RsvpStatus status) {
        requireExistingEvent(eventId);
        AttendanceState updated = attendanceStore.setRsvp(
                identity.key(),
                eventId,
                Objects.requireNonNull(status, "status is required"),
                Instant.now());
        publishCapacity(eventId);
        return updated;
    }

    public AttendanceState clearRsvp(VerifiedIdentity identity, UUID eventId) {
        requireExistingEvent(eventId);
        AttendanceState updated = attendanceStore.setRsvp(identity.key(), eventId, null, Instant.now());
        publishCapacity(eventId);
        return updated;
    }

    /**
     * Announces the new seat count to anyone watching.
     *
     * <p>Published after the store call returns, so watchers are never told about a seat
     * that a failed write never actually took.
     */
    private void publishCapacity(UUID eventId) {
        attendanceStore.findCapacity(eventId).ifPresent(capacityBroadcaster::publish);
    }

    /**
     * Seat availability for a published event.
     *
     * <p>A store with no capacity row reports the event as unbounded rather than failing:
     * an event that never declared a limit does not restrict RSVPs.
     */
    public EventCapacity findCapacity(UUID eventId) {
        requireExistingEvent(eventId);
        return attendanceStore.findCapacity(eventId)
                .orElseGet(() -> EventCapacity.unlimited(eventId));
    }

    private void requireExistingEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId is required");
        eventQueryService.findEvent(eventId);
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
