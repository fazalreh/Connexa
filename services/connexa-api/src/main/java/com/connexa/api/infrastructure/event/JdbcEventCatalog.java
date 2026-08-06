package com.connexa.api.infrastructure.event;

import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.infrastructure.persistence.SqlValues;
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
 * Durable public event catalog.
 *
 * <p>Both read paths constrain results to {@link EventStatus#PUBLISHED}. Discovery must
 * never surface a draft, a cancelled event, or an archived one, and the filter lives in
 * SQL rather than in a caller so it cannot be omitted at a call site.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcEventCatalog implements EventCatalog {

    private static final String COLUMNS = """
            id, title, summary, starts_at, ends_at, time_zone, venue_name,
            category, organizer_name, status, revision, created_at, updated_at,
            cover_image_url
            """;

    private static final RowMapper<EventSummary> ROW_MAPPER = (rs, rowNumber) -> new EventSummary(
            SqlValues.readUuid(rs, "id"),
            rs.getString("title"),
            rs.getString("summary"),
            SqlValues.readInstant(rs, "starts_at"),
            SqlValues.readInstant(rs, "ends_at"),
            rs.getString("time_zone"),
            rs.getString("venue_name"),
            rs.getString("category"),
            rs.getString("organizer_name"),
            SqlValues.readEnum(rs, "status", EventStatus.class),
            rs.getLong("revision"),
            SqlValues.readInstant(rs, "created_at"),
            SqlValues.readInstant(rs, "updated_at"),
            rs.getString("cover_image_url"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcEventCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventSummary> findPublished(EventQuery query) {
        Objects.requireNonNull(query, "query is required");

        StringBuilder predicate = new StringBuilder(" where status = :status");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", EventStatus.PUBLISHED.name());

        if (query.query() != null) {
            predicate.append(" and (lower(title) like :term escape '")
                    .append(SqlValues.LIKE_ESCAPE)
                    .append("' or lower(summary) like :term escape '")
                    .append(SqlValues.LIKE_ESCAPE)
                    .append("')");
            parameters.addValue("term", SqlValues.containsPattern(query.query()));
        }
        if (query.from() != null) {
            predicate.append(" and starts_at >= :from");
            parameters.addValue("from", SqlValues.toDatabase(query.from()));
        }
        if (query.to() != null) {
            predicate.append(" and starts_at <= :to");
            parameters.addValue("to", SqlValues.toDatabase(query.to()));
        }

        Long counted = jdbc.queryForObject(
                "select count(*) from events" + predicate, parameters, Long.class);
        long total = counted == null ? 0L : counted;

        long offset = (long) query.page() * query.size();
        if (offset >= total) {
            return new PageResponse<>(List.of(), query.page(), query.size(), total);
        }

        parameters.addValue("limit", query.size());
        parameters.addValue("offset", offset);
        // id breaks ties so paging stays stable across requests when two events share a start.
        List<EventSummary> items = jdbc.query(
                "select " + COLUMNS + " from events" + predicate
                        + " order by starts_at asc, id asc limit :limit offset :offset",
                parameters,
                ROW_MAPPER);
        return new PageResponse<>(items, query.page(), query.size(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventSummary> findById(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId is required");
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", eventId)
                .addValue("status", EventStatus.PUBLISHED.name());
        return jdbc.query(
                        "select " + COLUMNS + " from events where id = :id and status = :status",
                        parameters,
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }
}
