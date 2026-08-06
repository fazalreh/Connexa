package com.connexa.api.infrastructure.organizer;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
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
 * Durable storage for private organizer drafts.
 *
 * <p>Drafts are owner-scoped. {@code findByOwner} filters on the owning identity in SQL so a
 * draft cannot leak into another organizer's list, and re-saving an existing id replaces that
 * draft rather than creating a second copy.
 */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcOrganizerEventDraftStore implements OrganizerEventDraftStore {

    private static final String COLUMNS = """
            id, owner_issuer, owner_subject, title, description, location, starts_at, ends_at,
            time_zone, category, capacity, status, created_at, updated_at
            """;

    private static final RowMapper<OrganizerEventDraft> ROW_MAPPER =
            (rs, rowNumber) -> new OrganizerEventDraft(
                    SqlValues.readUuid(rs, "id"),
                    new IdentityKey(rs.getString("owner_issuer"), rs.getString("owner_subject")),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("location"),
                    SqlValues.readInstant(rs, "starts_at"),
                    SqlValues.readInstant(rs, "ends_at"),
                    rs.getString("time_zone"),
                    rs.getString("category"),
                    rs.getInt("capacity"),
                    SqlValues.readEnum(rs, "status", EventStatus.class),
                    SqlValues.readInstant(rs, "created_at"),
                    SqlValues.readInstant(rs, "updated_at"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOrganizerEventDraftStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    @Override
    @Transactional
    public OrganizerEventDraft save(OrganizerEventDraft draft) {
        Objects.requireNonNull(draft, "draft is required");

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", draft.id())
                .addValue("ownerIssuer", draft.owner().issuer())
                .addValue("ownerSubject", draft.owner().subject())
                .addValue("title", draft.title())
                .addValue("description", draft.description())
                .addValue("location", draft.location())
                .addValue("startsAt", SqlValues.toDatabase(draft.startsAt()))
                .addValue("endsAt", SqlValues.toDatabase(draft.endsAt()))
                .addValue("timeZone", draft.timeZone())
                .addValue("category", draft.category())
                .addValue("capacity", draft.capacity())
                .addValue("status", SqlValues.name(draft.status()))
                .addValue("createdAt", SqlValues.toDatabase(draft.createdAt()))
                .addValue("updatedAt", SqlValues.toDatabase(draft.updatedAt()));

        int updated = jdbc.update(
                "update organizer_event_drafts set owner_issuer = :ownerIssuer,"
                        + " owner_subject = :ownerSubject, title = :title, description = :description,"
                        + " location = :location, starts_at = :startsAt, ends_at = :endsAt,"
                        + " time_zone = :timeZone, category = :category, capacity = :capacity,"
                        + " status = :status, created_at = :createdAt, updated_at = :updatedAt"
                        + " where id = :id",
                parameters);

        if (updated == 0) {
            jdbc.update(
                    "insert into organizer_event_drafts"
                            + " (id, owner_issuer, owner_subject, title, description, location,"
                            + " starts_at, ends_at, time_zone, category, capacity, status,"
                            + " created_at, updated_at)"
                            + " values (:id, :ownerIssuer, :ownerSubject, :title, :description, :location,"
                            + " :startsAt, :endsAt, :timeZone, :category, :capacity, :status,"
                            + " :createdAt, :updatedAt)",
                    parameters);
        }
        return draft;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizerEventDraft> findByIdAndOwner(UUID draftId, IdentityKey owner) {
        Objects.requireNonNull(draftId, "draftId is required");
        Objects.requireNonNull(owner, "owner is required");
        return jdbc.query(
                        "select " + COLUMNS + " from organizer_event_drafts"
                                + " where id = :draftId and owner_issuer = :issuer"
                                + " and owner_subject = :subject",
                        new MapSqlParameterSource()
                                .addValue("draftId", draftId)
                                .addValue("issuer", owner.issuer())
                                .addValue("subject", owner.subject()),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizerEventDraft> findByOwner(IdentityKey owner) {
        Objects.requireNonNull(owner, "owner is required");
        return jdbc.query(
                "select " + COLUMNS + " from organizer_event_drafts"
                        + " where owner_issuer = :issuer and owner_subject = :subject"
                        + " order by updated_at desc, id asc",
                new MapSqlParameterSource()
                        .addValue("issuer", owner.issuer())
                        .addValue("subject", owner.subject()),
                ROW_MAPPER);
    }
}
