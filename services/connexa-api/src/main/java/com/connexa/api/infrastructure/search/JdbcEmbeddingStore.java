package com.connexa.api.infrastructure.search;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.search.EmbeddingVector;
import com.connexa.api.infrastructure.persistence.SqlValues;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Durable semantic index. */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcEmbeddingStore implements EmbeddingStore {

    private static final RowMapper<StoredEmbedding> ROW_MAPPER = (rs, rowNumber) -> new StoredEmbedding(
            SqlValues.readUuid(rs, "event_id"),
            EmbeddingVector.fromBytes(rs.getBytes("vector")),
            rs.getString("content_hash"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEmbeddingStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    @Override
    @Transactional
    public void save(StoredEmbedding embedding, String model) {
        Objects.requireNonNull(embedding, "embedding is required");
        Objects.requireNonNull(model, "model is required");

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", embedding.eventId())
                .addValue("model", model)
                .addValue("dimensions", embedding.vector().dimensions())
                .addValue("vector", embedding.vector().toBytes())
                .addValue("contentHash", embedding.contentHash())
                .addValue("embeddedAt", SqlValues.toDatabase(Instant.now()));

        int updated = jdbc.update(
                "update event_embeddings set model = :model, dimensions = :dimensions,"
                        + " vector = :vector, content_hash = :contentHash, embedded_at = :embeddedAt"
                        + " where event_id = :eventId",
                parameters);
        if (updated == 0) {
            jdbc.update(
                    "insert into event_embeddings"
                            + " (event_id, model, dimensions, vector, content_hash, embedded_at)"
                            + " values (:eventId, :model, :dimensions, :vector, :contentHash, :embeddedAt)",
                    parameters);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEmbedding> findAll(String model) {
        // Joined against the catalogue so a vector for an unpublished event can never
        // surface a result the caller is not allowed to see.
        return jdbc.query(
                "select e.event_id, e.vector, e.content_hash from event_embeddings e"
                        + " join events v on v.id = e.event_id"
                        + " where e.model = :model and v.status = :status",
                new MapSqlParameterSource()
                        .addValue("model", model)
                        .addValue("status", EventStatus.PUBLISHED.name()),
                ROW_MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findUnindexed(String model, int limit) {
        return jdbc.query(
                "select v.id from events v"
                        + " left join event_embeddings e"
                        + " on e.event_id = v.id and e.model = :model"
                        + " where v.status = :status and e.event_id is null"
                        + " order by v.created_at desc limit :limit",
                new MapSqlParameterSource()
                        .addValue("model", model)
                        .addValue("status", EventStatus.PUBLISHED.name())
                        .addValue("limit", limit),
                (rs, rowNumber) -> SqlValues.readUuid(rs, "id"));
    }
}
