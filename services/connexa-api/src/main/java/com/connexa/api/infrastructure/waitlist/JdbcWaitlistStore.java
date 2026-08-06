package com.connexa.api.infrastructure.waitlist;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.waitlist.WaitlistEntry;
import com.connexa.api.infrastructure.persistence.SqlValues;
import com.connexa.api.infrastructure.push.PushSender;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, fairly-ordered waitlist.
 *
 * <p>Promotion transfers a seat directly from the attendee giving it up to the one who has
 * waited longest. The seat is never momentarily free, which is what stops a bystander
 * refreshing the page and taking it ahead of the queue.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcWaitlistStore implements WaitlistStore {

    private static final String COLUMNS =
            "event_id, identity_issuer, identity_subject, position, joined_at, promoted_at";

    private static final RowMapper<WaitlistEntry> ROW_MAPPER = (rs, rowNumber) -> new WaitlistEntry(
            SqlValues.readUuid(rs, "event_id"),
            new IdentityKey(rs.getString("identity_issuer"), rs.getString("identity_subject")),
            rs.getLong("position"),
            SqlValues.readInstant(rs, "joined_at"),
            SqlValues.readInstant(rs, "promoted_at"));

    private final NamedParameterJdbcTemplate jdbc;
    private final PushSender pushSender;

    /** Convenience for tests and for deployments without push. */
    public JdbcWaitlistStore(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, PushSender.NONE);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcWaitlistStore(NamedParameterJdbcTemplate jdbc, PushSender pushSender) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
        this.pushSender = Objects.requireNonNull(pushSender, "pushSender is required");
    }

    /**
     * Adds an attendee to the back of the queue.
     *
     * <p>The position is derived inside the insert, and a unique index on
     * (event_id, position) rejects a collision. Two concurrent joins therefore cannot share
     * a place: the loser sees a duplicate key and retries against the new maximum.
     */
    @Transactional
    @Override
    public WaitlistEntry join(IdentityKey identity, UUID eventId, Instant joinedAt) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(joinedAt, "joinedAt is required");

        for (int attempt = 0; attempt < 5; attempt++) {
            long position = nextPosition(eventId);
            try {
                jdbc.update(
                        "insert into event_waitlist"
                                + " (event_id, identity_issuer, identity_subject, position, joined_at)"
                                + " values (:eventId, :issuer, :subject, :position, :joinedAt)",
                        parameters(identity, eventId)
                                .addValue("position", position)
                                .addValue("joinedAt", SqlValues.toDatabase(joinedAt)));
                return new WaitlistEntry(eventId, identity, position, joinedAt, null);
            } catch (DuplicateKeyException contended) {
                Optional<WaitlistEntry> existing = find(identity, eventId);
                if (existing.isPresent()) {
                    // The clash was this same attendee, not a race for a place.
                    return existing.get();
                }
            }
        }
        throw new IllegalStateException("Could not claim a waitlist position for event " + eventId);
    }

    @Transactional
    @Override
    public boolean leave(IdentityKey identity, UUID eventId) {
        return jdbc.update(
                "delete from event_waitlist where event_id = :eventId"
                        + " and identity_issuer = :issuer and identity_subject = :subject",
                parameters(identity, eventId)) > 0;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<WaitlistEntry> find(IdentityKey identity, UUID eventId) {
        return jdbc.query(
                        "select " + COLUMNS + " from event_waitlist where event_id = :eventId"
                                + " and identity_issuer = :issuer and identity_subject = :subject",
                        parameters(identity, eventId),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    /**
     * How many still-waiting attendees are ahead of this one.
     *
     * <p>Reported as a rank rather than the raw position: positions never reuse numbers, so
     * after a few promotions a raw position of 27 would badly misrepresent a queue of three.
     */
    @Transactional(readOnly = true)
    @Override
    public Optional<Long> placeInQueue(IdentityKey identity, UUID eventId) {
        return find(identity, eventId)
                .filter(WaitlistEntry::waiting)
                .map(entry -> {
                    Long ahead = jdbc.queryForObject(
                            "select count(*) from event_waitlist where event_id = :eventId"
                                    + " and promoted_at is null and position < :position",
                            new MapSqlParameterSource()
                                    .addValue("eventId", eventId)
                                    .addValue("position", entry.position()),
                            Long.class);
                    return (ahead == null ? 0L : ahead) + 1;
                });
    }

    @Transactional(readOnly = true)
    @Override
    public List<WaitlistEntry> waiting(UUID eventId) {
        return jdbc.query(
                "select " + COLUMNS + " from event_waitlist where event_id = :eventId"
                        + " and promoted_at is null order by position asc",
                new MapSqlParameterSource().addValue("eventId", eventId),
                ROW_MAPPER);
    }

    @Override
    @Transactional
    public boolean promoteNext(UUID eventId, Instant at) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(at, "at is required");

        Optional<WaitlistEntry> next = jdbc.query(
                        "select " + COLUMNS + " from event_waitlist where event_id = :eventId"
                                + " and promoted_at is null order by position asc limit 1",
                        new MapSqlParameterSource().addValue("eventId", eventId),
                        ROW_MAPPER)
                .stream()
                .findFirst();
        if (next.isEmpty()) {
            return false;
        }
        WaitlistEntry entry = next.get();

        // Claiming is conditional on the entry still being unpromoted, so two seats freed at
        // once cannot both be handed to the same person.
        int claimed = jdbc.update(
                "update event_waitlist set promoted_at = :at where event_id = :eventId"
                        + " and identity_issuer = :issuer and identity_subject = :subject"
                        + " and promoted_at is null",
                parameters(entry.identity(), eventId).addValue("at", SqlValues.toDatabase(at)));
        if (claimed == 0) {
            return false;
        }

        writeGoingAttendance(entry.identity(), eventId, at);

        // Told after the seat is theirs, so the message is never sent for a promotion
        // that a later failure rolls back.
        pushSender.notify(
                entry.identity(),
                "A seat opened up",
                "You are off the waitlist. Your place is confirmed.",
                eventId.toString());
        return true;
    }

    /**
     * Records the promoted attendee as GOING, preserving any saved marker they had.
     * Written here rather than through the attendance store so the whole promotion stays
     * in one statement sequence inside the releasing transaction.
     */
    private void writeGoingAttendance(IdentityKey identity, UUID eventId, Instant at) {
        MapSqlParameterSource parameters = parameters(identity, eventId)
                .addValue("updatedAt", SqlValues.toDatabase(at));
        int updated = jdbc.update(
                "update attendance set rsvp_status = 'GOING', updated_at = :updatedAt"
                        + " where identity_issuer = :issuer and identity_subject = :subject"
                        + " and event_id = :eventId",
                parameters);
        if (updated == 0) {
            jdbc.update(
                    "insert into attendance"
                            + " (identity_issuer, identity_subject, event_id, saved, rsvp_status, updated_at)"
                            + " values (:issuer, :subject, :eventId, false, 'GOING', :updatedAt)",
                    parameters);
        }
    }

    private long nextPosition(UUID eventId) {
        Long highest = jdbc.queryForObject(
                "select coalesce(max(position), 0) from event_waitlist where event_id = :eventId",
                new MapSqlParameterSource().addValue("eventId", eventId),
                Long.class);
        return (highest == null ? 0L : highest) + 1;
    }

    private static MapSqlParameterSource parameters(IdentityKey identity, UUID eventId) {
        return new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("issuer", identity.issuer())
                .addValue("subject", identity.subject());
    }
}
