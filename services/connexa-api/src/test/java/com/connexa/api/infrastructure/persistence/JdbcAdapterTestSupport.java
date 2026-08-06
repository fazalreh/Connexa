package com.connexa.api.infrastructure.persistence;

import com.connexa.api.domain.event.EventStatus;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Builds an isolated embedded database for one test class and applies the shipped
 * migration to it.
 *
 * <p>The adapters are constructed directly rather than through a Spring context, so the
 * {@code @Transactional} annotations on them are inert here. These tests cover SQL
 * behaviour and mapping; transaction boundaries are a deployment concern verified against
 * a real database.
 */
public final class JdbcAdapterTestSupport {

    private static final Pattern MIGRATION_VERSION = Pattern.compile("V(\\d+)__.*\\.sql");

    private JdbcAdapterTestSupport() {
    }

    /**
     * Creates a fresh in-memory database in PostgreSQL compatibility mode and applies the
     * shipped migrations. Each call uses a unique database name so test classes cannot
     * observe one another's rows.
     */
    public static NamedParameterJdbcTemplate freshDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:h2:mem:connexa-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        applyMigration(dataSource);
        return new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * Applies every shipped migration in version order, so a test database matches what a
     * deployment would have after a migrate.
     */
    private static void applyMigration(DataSource dataSource) {
        Resource[] migrations;
        try {
            migrations = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:db/migration/V*.sql");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read migrations", exception);
        }
        if (migrations.length == 0) {
            throw new IllegalStateException("No migrations found on the classpath");
        }
        // Sort numerically: a lexicographic sort would place V10 before V2.
        Arrays.sort(migrations, Comparator.comparingInt(JdbcAdapterTestSupport::versionOf));
        new ResourceDatabasePopulator(migrations).execute(dataSource);
    }

    private static int versionOf(Resource migration) {
        String filename = Objects.requireNonNull(migration.getFilename(), "migration filename");
        Matcher matcher = MIGRATION_VERSION.matcher(filename);
        if (!matcher.matches()) {
            throw new IllegalStateException("Unexpected migration filename: " + filename);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Inserts an event row directly. {@link com.connexa.api.infrastructure.event.EventCatalog}
     * is a read boundary, so the catalog tests need a way to seed rows that does not depend
     * on a write path that does not exist yet.
     */
    public static UUID insertEvent(
            NamedParameterJdbcTemplate jdbc,
            String title,
            String summary,
            Instant startsAt,
            EventStatus status) {
        return insertEvent(jdbc, title, summary, startsAt, status, null);
    }

    /**
     * Inserts an event with a declared seat limit. A null {@code totalCapacity} leaves the
     * event unbounded.
     */
    public static UUID insertEvent(
            NamedParameterJdbcTemplate jdbc,
            String title,
            String summary,
            Instant startsAt,
            EventStatus status,
            Integer totalCapacity) {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        jdbc.update(
                "insert into events (id, title, summary, starts_at, ends_at, time_zone,"
                        + " venue_name, category, organizer_name, status, revision,"
                        + " created_at, updated_at, total_capacity, reserved_count)"
                        + " values (:id, :title, :summary, :startsAt, :endsAt, :timeZone,"
                        + " :venueName, :category, :organizerName, :status, :revision,"
                        + " :createdAt, :updatedAt, :totalCapacity, 0)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("title", title)
                        .addValue("summary", summary)
                        .addValue("startsAt", SqlValues.toDatabase(startsAt))
                        .addValue("endsAt", SqlValues.toDatabase(startsAt.plusSeconds(3_600)))
                        .addValue("timeZone", "Asia/Karachi")
                        .addValue("venueName", "Main Auditorium")
                        .addValue("category", "Technology")
                        .addValue("organizerName", "Connexa Events")
                        .addValue("status", status.name())
                        .addValue("revision", 0L)
                        .addValue("createdAt", SqlValues.toDatabase(createdAt))
                        .addValue("updatedAt", SqlValues.toDatabase(createdAt))
                        .addValue("totalCapacity", totalCapacity));
        return id;
    }
}
