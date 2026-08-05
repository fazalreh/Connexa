package com.connexa.api.application;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.attendance.AttendanceStore;
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

    public AttendanceService(AttendanceStore attendanceStore, EventQueryService eventQueryService) {
        this.attendanceStore = Objects.requireNonNull(attendanceStore, "attendanceStore is required");
        this.eventQueryService = Objects.requireNonNull(eventQueryService, "eventQueryService is required");
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
        return attendanceStore.setRsvp(
                identity.key(),
                eventId,
                Objects.requireNonNull(status, "status is required"),
                Instant.now());
    }

    public AttendanceState clearRsvp(VerifiedIdentity identity, UUID eventId) {
        requireExistingEvent(eventId);
        return attendanceStore.setRsvp(identity.key(), eventId, null, Instant.now());
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
