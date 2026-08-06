package com.connexa.api.infrastructure.checkin;

import com.connexa.api.domain.checkin.NotAttendingException;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.infrastructure.persistence.SqlValues;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable door check-in.
 *
 * <p>The write is conditional on {@code checked_in_at} still being null, so a second scan
 * cannot overwrite a recorded arrival time — at a busy door the same person being scanned
 * twice is routine, and the first reading is the true one.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcCheckInStore implements CheckInStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCheckInStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    @Override
    @Transactional
    public CheckInResult checkIn(IdentityKey identity, UUID eventId, Instant at) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(at, "at is required");

        MapSqlParameterSource parameters = parameters(identity, eventId)
                .addValue("at", SqlValues.toDatabase(at));

        // Guarded on rsvp_status so a validly-signed pass from someone who has since
        // cancelled cannot be honoured, and on checked_in_at so the first scan wins.
        int recorded = jdbc.update(
                "update attendance set checked_in_at = :at"
                        + " where identity_issuer = :issuer and identity_subject = :subject"
                        + " and event_id = :eventId and rsvp_status = 'GOING'"
                        + " and checked_in_at is null",
                parameters);
        if (recorded == 1) {
            return new CheckInResult(at, true);
        }

        // Nothing was written: either they are already in, or they hold no seat.
        return findCheckIn(identity, eventId)
                .map(existing -> new CheckInResult(existing, false))
                .orElseThrow(() -> new NotAttendingException(eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findCheckIn(IdentityKey identity, UUID eventId) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        return jdbc.query(
                        "select checked_in_at from attendance"
                                + " where identity_issuer = :issuer and identity_subject = :subject"
                                + " and event_id = :eventId",
                        parameters(identity, eventId),
                        (rs, rowNumber) -> SqlValues.readInstant(rs, "checked_in_at"))
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public long countCheckedIn(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId is required");
        Long counted = jdbc.queryForObject(
                "select count(*) from attendance where event_id = :eventId"
                        + " and checked_in_at is not null",
                new MapSqlParameterSource().addValue("eventId", eventId),
                Long.class);
        return counted == null ? 0L : counted;
    }

    private static MapSqlParameterSource parameters(IdentityKey identity, UUID eventId) {
        return new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("issuer", identity.issuer())
                .addValue("subject", identity.subject());
    }
}
