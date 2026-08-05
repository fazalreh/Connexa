package com.connexa.api.infrastructure.attendance;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Ephemeral local state used only until a production persistence adapter is added.
 * All state is scoped to a verified identity key.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class InMemoryAttendanceStore implements AttendanceStore {

    private final ConcurrentMap<IdentityKey, ConcurrentMap<UUID, AttendanceState>> stateByIdentity =
            new ConcurrentHashMap<>();

    @Override
    public Optional<AttendanceState> find(IdentityKey identity, UUID eventId) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        ConcurrentMap<UUID, AttendanceState> stateByEvent = stateByIdentity.get(identity);
        if (stateByEvent == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stateByEvent.get(eventId)).filter(AttendanceState::hasState);
    }

    @Override
    public List<AttendanceState> findAll(IdentityKey identity) {
        Objects.requireNonNull(identity, "identity is required");
        ConcurrentMap<UUID, AttendanceState> stateByEvent = stateByIdentity.get(identity);
        if (stateByEvent == null) {
            return List.of();
        }
        return stateByEvent.values().stream()
                .filter(AttendanceState::hasState)
                .sorted(Comparator.comparing(AttendanceState::updatedAt).reversed())
                .toList();
    }

    @Override
    public AttendanceState setSaved(IdentityKey identity, UUID eventId, boolean saved, Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        return update(identity, eventId, current -> new AttendanceState(
                eventId,
                saved,
                current == null ? null : current.rsvpStatus(),
                saved || (current != null && current.rsvpStatus() != null) ? updatedAt : null));
    }

    @Override
    public AttendanceState setRsvp(
            IdentityKey identity,
            UUID eventId,
            RsvpStatus rsvpStatus,
            Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        return update(identity, eventId, current -> new AttendanceState(
                eventId,
                current != null && current.saved(),
                rsvpStatus,
                rsvpStatus != null || (current != null && current.saved()) ? updatedAt : null));
    }

    private AttendanceState update(
            IdentityKey identity,
            UUID eventId,
            StateUpdate update) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        ConcurrentMap<UUID, AttendanceState> stateByEvent =
                stateByIdentity.computeIfAbsent(identity, ignored -> new ConcurrentHashMap<>());
        return stateByEvent.compute(eventId, (ignored, current) -> update.apply(current));
    }

    @FunctionalInterface
    private interface StateUpdate {
        AttendanceState apply(AttendanceState current);
    }
}
