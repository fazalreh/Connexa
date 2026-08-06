package com.connexa.api.infrastructure.publication;

import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.organizer.DraftAlreadyPublishedException;
import com.connexa.api.domain.organizer.OrganizerDraftNotFoundException;
import com.connexa.api.infrastructure.persistence.SqlValues;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable publication: writes the public event row and links it to its draft.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcEventPublicationStore implements EventPublicationStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEventPublicationStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    @Override
    @Transactional
    public EventSummary publish(UUID draftId, EventSummary event, Integer totalCapacity) {
        Objects.requireNonNull(draftId, "draftId is required");
        Objects.requireNonNull(event, "event is required");

        Optional<UUID> alreadyPublished = findPublishedEventId(draftId);
        if (alreadyPublished.isPresent()) {
            throw new DraftAlreadyPublishedException(draftId, alreadyPublished.get());
        }

        insertEvent(event, totalCapacity);

        // Claiming the link is conditional on it still being unset, so two concurrent
        // publishes of the same draft cannot both succeed; the loser rolls back the event
        // row it just inserted.
        int linked = jdbc.update(
                "update organizer_event_drafts set published_event_id = :eventId"
                        + " where id = :draftId and published_event_id is null",
                new MapSqlParameterSource()
                        .addValue("eventId", event.id())
                        .addValue("draftId", draftId));
        if (linked == 0) {
            throw findPublishedEventId(draftId)
                    .map(winner -> (RuntimeException) new DraftAlreadyPublishedException(draftId, winner))
                    .orElseGet(() -> new OrganizerDraftNotFoundException(draftId));
        }
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findPublishedEventId(UUID draftId) {
        Objects.requireNonNull(draftId, "draftId is required");
        return jdbc.query(
                        "select published_event_id from organizer_event_drafts where id = :draftId",
                        new MapSqlParameterSource().addValue("draftId", draftId),
                        (rs, rowNumber) -> SqlValues.readUuid(rs, "published_event_id"))
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private void insertEvent(EventSummary event, Integer totalCapacity) {
        jdbc.update(
                "insert into events (id, title, summary, starts_at, ends_at, time_zone,"
                        + " venue_name, category, organizer_name, status, revision,"
                        + " created_at, updated_at, total_capacity, reserved_count)"
                        + " values (:id, :title, :summary, :startsAt, :endsAt, :timeZone,"
                        + " :venueName, :category, :organizerName, :status, :revision,"
                        + " :createdAt, :updatedAt, :totalCapacity, 0)",
                new MapSqlParameterSource()
                        .addValue("id", event.id())
                        .addValue("title", event.title())
                        .addValue("summary", event.summary())
                        .addValue("startsAt", SqlValues.toDatabase(event.startsAt()))
                        .addValue("endsAt", SqlValues.toDatabase(event.endsAt()))
                        .addValue("timeZone", event.timeZone())
                        .addValue("venueName", event.venueName())
                        .addValue("category", event.category())
                        .addValue("organizerName", event.organizerName())
                        .addValue("status", SqlValues.name(event.status()))
                        .addValue("revision", event.revision())
                        .addValue("createdAt", SqlValues.toDatabase(event.createdAt()))
                        .addValue("updatedAt", SqlValues.toDatabase(event.updatedAt()))
                        .addValue("totalCapacity", totalCapacity));
    }
}
