package com.connexa.api.infrastructure.push;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.infrastructure.persistence.SqlValues;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Durable device registry. */
@Repository
@ConditionalOnProperty(name = "connexa.persistence.mode", havingValue = "postgres")
public class JdbcPushTokenStore implements PushTokenStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPushTokenStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc is required");
    }

    @Override
    @Transactional
    public void register(IdentityKey identity, String token, String platform, Instant at) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(token, "token is required");

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("token", token)
                .addValue("issuer", identity.issuer())
                .addValue("subject", identity.subject())
                .addValue("platform", platform == null ? "android" : platform)
                .addValue("at", SqlValues.toDatabase(at));

        // The update reassigns the identity, so a device handed to another person stops
        // receiving the previous owner's notifications.
        int updated = jdbc.update(
                "update push_tokens set identity_issuer = :issuer, identity_subject = :subject,"
                        + " platform = :platform, last_seen_at = :at where token = :token",
                parameters);
        if (updated == 0) {
            jdbc.update(
                    "insert into push_tokens"
                            + " (token, identity_issuer, identity_subject, platform,"
                            + " registered_at, last_seen_at)"
                            + " values (:token, :issuer, :subject, :platform, :at, :at)",
                    parameters);
        }
    }

    @Override
    @Transactional
    public boolean unregister(String token) {
        return jdbc.update("delete from push_tokens where token = :token",
                new MapSqlParameterSource().addValue("token", token)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> tokensFor(IdentityKey identity) {
        Objects.requireNonNull(identity, "identity is required");
        return jdbc.query(
                "select token from push_tokens"
                        + " where identity_issuer = :issuer and identity_subject = :subject",
                new MapSqlParameterSource()
                        .addValue("issuer", identity.issuer())
                        .addValue("subject", identity.subject()),
                (rs, rowNumber) -> rs.getString("token"));
    }
}
