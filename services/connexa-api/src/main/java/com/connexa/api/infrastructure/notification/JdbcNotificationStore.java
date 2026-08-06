package com.connexa.api.infrastructure.notification;

import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.notification.NotificationItem;
import com.connexa.api.domain.notification.NotificationType;
import com.connexa.api.infrastructure.persistence.SqlValues;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, identity-scoped notification inbox.
 *
 * <p>{@code markRead} matches on both the notification id and the caller's identity, so a
 * caller holding another identity's notification id cannot change its state.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcNotificationStore implements NotificationStore {

    private static final String COLUMNS = "id, type, title, body, event_id, created_at, read_at";

    private static final RowMapper<NotificationItem> ROW_MAPPER = (rs, rowNumber) -> new NotificationItem(
            SqlValues.readUuid(rs, "id"),
            SqlValues.readEnum(rs, "type", NotificationType.class),
            rs.getString("title"),
            rs.getString("body"),
            SqlValues.readUuid(rs, "event_id"),
            SqlValues.readInstant(rs, "created_at"),
            SqlValues.readInstant(rs, "read_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationItem> findFor(IdentityKey identity, int page, int size) {
        Objects.requireNonNull(identity, "identity is required");
        validatePage(page, size);

        MapSqlParameterSource parameters = identityParameters(identity);
        Long counted = jdbc.queryForObject(
                "select count(*) from notifications"
                        + " where identity_issuer = :issuer and identity_subject = :subject",
                parameters,
                Long.class);
        long total = counted == null ? 0L : counted;

        long offset = (long) page * size;
        if (offset >= total) {
            return new PageResponse<>(List.of(), page, size, total);
        }

        parameters.addValue("limit", size).addValue("offset", offset);
        List<NotificationItem> items = jdbc.query(
                "select " + COLUMNS + " from notifications"
                        + " where identity_issuer = :issuer and identity_subject = :subject"
                        + " order by created_at desc, id asc limit :limit offset :offset",
                parameters,
                ROW_MAPPER);
        return new PageResponse<>(items, page, size, total);
    }

    @Override
    @Transactional
    public Optional<NotificationItem> markRead(
            IdentityKey identity, UUID notificationId, Instant readAt) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(notificationId, "notificationId is required");
        Objects.requireNonNull(readAt, "readAt is required");

        MapSqlParameterSource parameters = identityParameters(identity)
                .addValue("id", notificationId);

        Optional<NotificationItem> existing = jdbc.query(
                        "select " + COLUMNS + " from notifications"
                                + " where id = :id and identity_issuer = :issuer"
                                + " and identity_subject = :subject",
                        parameters,
                        ROW_MAPPER)
                .stream()
                .findFirst();

        if (existing.isEmpty()) {
            return Optional.empty();
        }
        NotificationItem item = existing.get();
        // Re-reading an already-read notification keeps the original timestamp.
        if (item.read()) {
            return Optional.of(item);
        }

        NotificationItem updated = item.markRead(readAt);
        jdbc.update(
                "update notifications set read_at = :readAt"
                        + " where id = :id and identity_issuer = :issuer"
                        + " and identity_subject = :subject",
                parameters.addValue("readAt", SqlValues.toDatabase(updated.readAt())));
        return Optional.of(updated);
    }

    private static MapSqlParameterSource identityParameters(IdentityKey identity) {
        return new MapSqlParameterSource()
                .addValue("issuer", identity.issuer())
                .addValue("subject", identity.subject());
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
