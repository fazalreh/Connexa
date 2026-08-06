package com.connexa.api.infrastructure.attendance;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.EventAtCapacityException;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventCapacity;
import com.connexa.api.domain.event.EventNotFoundException;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.infrastructure.persistence.SqlValues;
import com.connexa.api.infrastructure.waitlist.WaitlistPromoter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable saved-event and RSVP state, scoped to a verified identity, including seat
 * reservation against an event's capacity.
 *
 * <p>Saving and responding are independent: changing one must not clear the other. Each
 * mutation therefore reads the current row, merges the single field it owns, and writes the
 * result back inside one transaction.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcAttendanceStore implements AttendanceStore {

    private static final String COLUMNS = "event_id, saved, rsvp_status, updated_at";

    private static final RowMapper<AttendanceState> ROW_MAPPER = (rs, rowNumber) -> new AttendanceState(
            SqlValues.readUuid(rs, "event_id"),
            rs.getBoolean("saved"),
            SqlValues.readEnum(rs, "rsvp_status", RsvpStatus.class),
            SqlValues.readInstant(rs, "updated_at"));

    private static final RowMapper<EventCapacity> CAPACITY_ROW_MAPPER = (rs, rowNumber) -> {
        int declared = rs.getInt("total_capacity");
        Integer totalCapacity = rs.wasNull() ? null : declared;
        return new EventCapacity(
                SqlValues.readUuid(rs, "id"), totalCapacity, rs.getInt("reserved_count"));
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final WaitlistPromoter waitlistPromoter;

    /** Convenience for tests and for deployments running without a waitlist. */
    public JdbcAttendanceStore(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, WaitlistPromoter.NONE);
    }

    /**
     * With two public constructors the container cannot pick one on its own, so the
     * injection point is stated explicitly rather than left to resolution order.
     */
    @Autowired
    public JdbcAttendanceStore(NamedParameterJdbcTemplate jdbc, WaitlistPromoter waitlistPromoter) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
        this.waitlistPromoter = Objects.requireNonNull(waitlistPromoter, "waitlistPromoter is required");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttendanceState> find(IdentityKey identity, UUID eventId) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        return readCurrent(identity, eventId).filter(AttendanceState::hasState);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceState> findAll(IdentityKey identity) {
        Objects.requireNonNull(identity, "identity is required");
        return jdbc.query(
                "select " + COLUMNS + " from attendance"
                        + " where identity_issuer = :issuer and identity_subject = :subject"
                        + " and (saved = true or rsvp_status is not null)"
                        + " order by updated_at desc, event_id asc",
                identityParameters(identity),
                ROW_MAPPER);
    }

    @Override
    @Transactional
    public AttendanceState setSaved(IdentityKey identity, UUID eventId, boolean saved, Instant updatedAt) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        AttendanceState current = readCurrent(identity, eventId).orElse(null);
        RsvpStatus keptStatus = current == null ? null : current.rsvpStatus();
        AttendanceState next = new AttendanceState(
                eventId,
                saved,
                keptStatus,
                saved || keptStatus != null ? updatedAt : null);

        write(identity, next, current == null);
        return next;
    }

    @Override
    @Transactional
    public AttendanceState setRsvp(
            IdentityKey identity, UUID eventId, RsvpStatus rsvpStatus, Instant updatedAt) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        AttendanceState current = readCurrent(identity, eventId).orElse(null);
        boolean heldSeat = current != null && current.rsvpStatus() == RsvpStatus.GOING;
        boolean wantsSeat = rsvpStatus == RsvpStatus.GOING;

        // Only a change of seat-holding status moves the counter, so repeating the same
        // RSVP is a no-op and a retried request cannot consume a second seat.
        if (wantsSeat && !heldSeat) {
            reserveSeatOrFail(eventId);
        } else if (heldSeat && !wantsSeat) {
            // Hand the seat straight to whoever has waited longest. Releasing it first
            // would briefly expose it to anyone refreshing the page, letting them take
            // it ahead of the queue.
            if (!waitlistPromoter.promoteNext(eventId, updatedAt)) {
                releaseSeat(eventId);
            }
        }

        boolean keptSaved = current != null && current.saved();
        AttendanceState next = new AttendanceState(
                eventId,
                keptSaved,
                rsvpStatus,
                rsvpStatus != null || keptSaved ? updatedAt : null);

        // Sharing the transaction with the counter change means a failure here rolls the
        // reservation back rather than stranding a seat nobody holds.
        write(identity, next, current == null);
        return next;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventCapacity> findCapacity(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId is required");
        return jdbc.query(
                        "select id, total_capacity, reserved_count from events where id = :eventId",
                        new MapSqlParameterSource().addValue("eventId", eventId),
                        CAPACITY_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    /**
     * Claims one seat. The guard lives in the {@code where} clause so the read of
     * {@code reserved_count} and the increment are a single statement; two callers racing
     * for the last seat serialize on the row lock and the loser matches no rows.
     */
    private void reserveSeatOrFail(UUID eventId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("status", EventStatus.PUBLISHED.name());

        int reserved = jdbc.update(
                "update events set reserved_count = reserved_count + 1"
                        + " where id = :eventId and status = :status"
                        + " and (total_capacity is null or reserved_count < total_capacity)",
                parameters);
        if (reserved == 1) {
            return;
        }

        // No row matched. Distinguish an event that cannot be booked at all from one that
        // is merely full, so the caller sees 404 rather than a misleading 409.
        Long publishedMatches = jdbc.queryForObject(
                "select count(*) from events where id = :eventId and status = :status",
                parameters,
                Long.class);
        if (publishedMatches == null || publishedMatches == 0L) {
            throw new EventNotFoundException(eventId);
        }
        throw new EventAtCapacityException(eventId);
    }

    /**
     * Returns a seat. The {@code reserved_count > 0} guard makes a duplicate release a
     * no-op rather than driving the counter negative.
     */
    private void releaseSeat(UUID eventId) {
        jdbc.update(
                "update events set reserved_count = reserved_count - 1"
                        + " where id = :eventId and reserved_count > 0",
                new MapSqlParameterSource().addValue("eventId", eventId));
    }

    private void write(IdentityKey identity, AttendanceState next, boolean isNewRow) {
        MapSqlParameterSource parameters = identityParameters(identity)
                .addValue("eventId", next.eventId())
                .addValue("saved", next.saved())
                .addValue("rsvpStatus", SqlValues.name(next.rsvpStatus()))
                .addValue("updatedAt", SqlValues.toDatabase(next.updatedAt()));

        if (isNewRow) {
            jdbc.update(
                    "insert into attendance"
                            + " (identity_issuer, identity_subject, event_id, saved, rsvp_status, updated_at)"
                            + " values (:issuer, :subject, :eventId, :saved, :rsvpStatus, :updatedAt)",
                    parameters);
        } else {
            jdbc.update(
                    "update attendance set saved = :saved, rsvp_status = :rsvpStatus,"
                            + " updated_at = :updatedAt"
                            + " where identity_issuer = :issuer and identity_subject = :subject"
                            + " and event_id = :eventId",
                    parameters);
        }
    }

    private Optional<AttendanceState> readCurrent(IdentityKey identity, UUID eventId) {
        return jdbc.query(
                        "select " + COLUMNS + " from attendance"
                                + " where identity_issuer = :issuer and identity_subject = :subject"
                                + " and event_id = :eventId",
                        identityParameters(identity).addValue("eventId", eventId),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    private static MapSqlParameterSource identityParameters(IdentityKey identity) {
        return new MapSqlParameterSource()
                .addValue("issuer", identity.issuer())
                .addValue("subject", identity.subject());
    }
}
