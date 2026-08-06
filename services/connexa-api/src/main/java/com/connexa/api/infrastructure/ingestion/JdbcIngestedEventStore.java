package com.connexa.api.infrastructure.ingestion;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.infrastructure.event.EventCatalog;
import com.connexa.api.infrastructure.persistence.SqlValues;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, idempotent sink for announcement-derived events.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcIngestedEventStore implements IngestedEventStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final EventCatalog eventCatalog;

    public JdbcIngestedEventStore(NamedParameterJdbcTemplate jdbc, EventCatalog eventCatalog) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
        this.eventCatalog = Objects.requireNonNull(eventCatalog, "eventCatalog is required");
    }

    @Override
    @Transactional
    public IngestionOutcome storeOnce(
            String sourceSystem,
            String sourceRecordId,
            String contentHash,
            EventSummary event,
            Integer totalCapacity) {
        Objects.requireNonNull(sourceSystem, "sourceSystem is required");
        Objects.requireNonNull(sourceRecordId, "sourceRecordId is required");
        Objects.requireNonNull(event, "event is required");

        Optional<UUID> existing = findExistingEventId(sourceSystem, sourceRecordId);
        if (existing.isPresent()) {
            return existingOutcome(existing.get(), event);
        }

        insertEvent(event, totalCapacity);
        try {
            jdbc.update(
                    "insert into ingested_events"
                            + " (source_system, source_record_id, event_id, content_hash, ingested_at)"
                            + " values (:sourceSystem, :sourceRecordId, :eventId, :contentHash, :ingestedAt)",
                    new MapSqlParameterSource()
                            .addValue("sourceSystem", sourceSystem)
                            .addValue("sourceRecordId", sourceRecordId)
                            .addValue("eventId", event.id())
                            .addValue("contentHash", contentHash)
                            .addValue("ingestedAt", SqlValues.toDatabase(event.createdAt())));
        } catch (DuplicateKeyException raced) {
            // Another worker claimed this message between the lookup and the insert.
            // The constraint is the authority; this transaction rolls back its event row
            // and the caller is told about the one that won.
            throw raced;
        }
        return new IngestionOutcome(event, true);
    }

    private IngestionOutcome existingOutcome(UUID eventId, EventSummary submitted) {
        // Prefer the stored event so the caller sees what is actually published; fall
        // back to the submission only if the row is no longer publicly visible.
        return new IngestionOutcome(eventCatalog.findById(eventId).orElse(submitted), false);
    }

    private Optional<UUID> findExistingEventId(String sourceSystem, String sourceRecordId) {
        return jdbc.query(
                        "select event_id from ingested_events"
                                + " where source_system = :sourceSystem"
                                + " and source_record_id = :sourceRecordId",
                        new MapSqlParameterSource()
                                .addValue("sourceSystem", sourceSystem)
                                .addValue("sourceRecordId", sourceRecordId),
                        (rs, rowNumber) -> SqlValues.readUuid(rs, "event_id"))
                .stream()
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

    /** Guards against a caller submitting an event in a status discovery would hide. */
    static void requirePublished(EventSummary event) {
        if (event.status() != EventStatus.PUBLISHED) {
            throw new IllegalArgumentException("ingested events must be published");
        }
    }
}
