package com.connexa.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.connexa.api.domain.attendance.EventAtCapacityException;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.EventPublication;
import com.connexa.api.domain.organizer.OrganizerDraftNotFoundException;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import com.connexa.api.infrastructure.attendance.AttendanceStore;
import com.connexa.api.infrastructure.attendance.JdbcAttendanceStore;
import com.connexa.api.infrastructure.event.EventCatalog;
import com.connexa.api.infrastructure.event.JdbcEventCatalog;
import com.connexa.api.infrastructure.organizer.JdbcOrganizerEventDraftStore;
import com.connexa.api.infrastructure.organizer.OrganizerEventDraftStore;
import com.connexa.api.infrastructure.publication.EventPublicationStore;
import com.connexa.api.infrastructure.publication.JdbcEventPublicationStore;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifies the behaviour that only a real database can demonstrate.
 *
 * <p>The adapter unit tests construct their stores with {@code new}, so no proxy exists and
 * {@code @Transactional} does nothing there. They prove the SQL is right; they cannot prove
 * that a failure part-way through a multi-statement operation undoes the earlier statements.
 * That is what this suite covers, against a genuine PostgreSQL server rather than a
 * compatibility mode.
 */
@SpringBootTest(properties = {
        "connexa.persistence.mode=postgres",
        "spring.flyway.enabled=true"
})
class PostgresTransactionIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");

    @Autowired
    private EventPublicationStore publicationStore;

    @Autowired
    private OrganizerEventDraftStore draftStore;

    @Autowired
    private AttendanceStore attendanceStore;

    @Autowired
    private EventCatalog eventCatalog;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to start embedded PostgreSQL", exception);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // Built from the port rather than a helper so the URL shape does not depend on the
        // embedded-postgres version.
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:" + POSTGRES.getPort() + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.getJdbcTemplate().execute(
                "truncate table event_waitlist, attendance, organizer_event_drafts, events cascade");
    }

    @Test
    @DisplayName("postgres mode wires the durable adapters and drops the in-memory ones")
    void durableAdaptersAreActive() {
        assertThat(eventCatalog).isInstanceOf(JdbcEventCatalog.class);
        assertThat(attendanceStore).isInstanceOf(JdbcAttendanceStore.class);
        assertThat(draftStore).isInstanceOf(JdbcOrganizerEventDraftStore.class);
        assertThat(publicationStore).isInstanceOf(JdbcEventPublicationStore.class);
    }

    @Test
    @DisplayName("flyway applied every shipped migration")
    void migrationsApplied() {
        // Derived from the files rather than a fixed list: this must keep asserting
        // "all of them" as migrations are added, not "the three that existed once".
        List<String> expected = shippedMigrationVersions();
        List<String> applied = jdbc.getJdbcTemplate().queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank",
                String.class);

        assertThat(expected).isNotEmpty();
        assertThat(applied).containsExactlyElementsOf(expected);
    }

    private static List<String> shippedMigrationVersions() {
        Pattern versioned = Pattern.compile("V(\\d+)__.*\\.sql");
        try {
            Resource[] migrations = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:db/migration/V*.sql");
            return Arrays.stream(migrations)
                    .map(Resource::getFilename)
                    .filter(Objects::nonNull)
                    .map(versioned::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to list migrations", exception);
        }
    }

    @Test
    @DisplayName("a failed publish rolls back the event row it had already inserted")
    void failedPublishLeavesNoOrphanEvent() {
        OrganizerEventDraft draft = savedDraft(50);
        EventSummary event = EventPublication.from(draft, UUID.randomUUID(), "Alice Khan", NOW);
        UUID unknownDraftId = UUID.randomUUID();

        assertThatThrownBy(() -> publicationStore.publish(unknownDraftId, event, 50))
                .isInstanceOf(OrganizerDraftNotFoundException.class);

        // The insert ran before the link failed. Without a transaction it would survive as a
        // listing that no draft points at.
        assertThat(countEvents()).isZero();
        assertThat(eventCatalog.findById(event.id())).isEmpty();
    }

    @Test
    @DisplayName("a reservation joins the caller's transaction and unwinds with it")
    void reservationRollsBackWithTheCallersTransaction() {
        UUID eventId = publishedEvent(10);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            attendanceStore.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);
            // Stands in for any later failure in the same request.
            throw new IllegalStateException("failure after the seat was taken");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(reservedCount(eventId)).isZero();
        assertThat(attendanceStore.find(ALICE, eventId)).isEmpty();
    }

    @Test
    @DisplayName("a released seat also unwinds when the caller's transaction fails")
    void releaseRollsBackWithTheCallersTransaction() {
        UUID eventId = publishedEvent(10);
        attendanceStore.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            attendanceStore.setRsvp(ALICE, eventId, null, NOW.plusSeconds(60));
            throw new IllegalStateException("failure after the seat was released");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(reservedCount(eventId)).isEqualTo(1);
        assertThat(attendanceStore.find(ALICE, eventId).orElseThrow().rsvpStatus())
                .isEqualTo(RsvpStatus.GOING);
    }

    @Test
    @DisplayName("a committed reservation survives the transaction")
    void committedReservationPersists() {
        UUID eventId = publishedEvent(10);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        transactions.executeWithoutResult(status -> attendanceStore.setRsvp(
                ALICE, eventId, RsvpStatus.GOING, NOW));

        assertThat(reservedCount(eventId)).isEqualTo(1);
        assertThat(attendanceStore.find(ALICE, eventId).orElseThrow().rsvpStatus())
                .isEqualTo(RsvpStatus.GOING);
    }

    @Test
    @DisplayName("the database refuses an overbooked count even when written directly")
    void checkConstraintBlocksOverbooking() {
        UUID eventId = publishedEvent(2);

        assertThatThrownBy(() -> jdbc.update(
                "update events set reserved_count = 5 where id = :id",
                new MapSqlParameterSource().addValue("id", eventId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(reservedCount(eventId)).isZero();
    }

    @Test
    @DisplayName("concurrent attendees cannot overbook on a real server")
    void concurrentReservationsNeverExceedCapacityOnPostgres() throws Exception {
        int capacity = 5;
        int contenders = 40;
        UUID eventId = publishedEvent(capacity);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Boolean>> attempts = IntStream.range(0, contenders)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        try {
                            attendanceStore.setRsvp(
                                    new IdentityKey("https://identity.connexa", "attendee-" + index),
                                    eventId,
                                    RsvpStatus.GOING,
                                    NOW);
                            return Boolean.TRUE;
                        } catch (EventAtCapacityException expected) {
                            return Boolean.FALSE;
                        }
                    })
                    .toList();

            long granted = 0;
            for (Future<Boolean> result : pool.invokeAll(attempts)) {
                if (result.get()) {
                    granted++;
                }
            }

            assertThat(granted).isEqualTo(capacity);
            assertThat(reservedCount(eventId)).isEqualTo(capacity);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("only one of two concurrent publishes of a draft wins")
    void concurrentPublishProducesOneListing() throws Exception {
        OrganizerEventDraft draft = savedDraft(20);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> attempts = IntStream.range(0, 2)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        try {
                            publicationStore.publish(
                                    draft.id(),
                                    EventPublication.from(
                                            draft, UUID.randomUUID(), "Alice Khan", NOW),
                                    draft.capacity());
                            return Boolean.TRUE;
                        } catch (RuntimeException expected) {
                            return Boolean.FALSE;
                        }
                    })
                    .toList();

            long published = 0;
            for (Future<Boolean> result : pool.invokeAll(attempts)) {
                if (result.get()) {
                    published++;
                }
            }

            assertThat(published).isEqualTo(1);
            // The loser's event insert must not survive as a duplicate listing.
            assertThat(countEvents()).isEqualTo(1);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }
    }

    private OrganizerEventDraft savedDraft(int capacity) {
        return draftStore.save(new OrganizerEventDraft(
                UUID.randomUUID(),
                ALICE,
                "Robotics Workshop",
                "A hands-on session covering the full build and test workflow.",
                "Main Auditorium",
                STARTS,
                STARTS.plusSeconds(7_200),
                "Asia/Karachi",
                "Technology",
                capacity,
                EventStatus.DRAFT,
                NOW,
                NOW));
    }

    private UUID publishedEvent(int capacity) {
        OrganizerEventDraft draft = savedDraft(capacity);
        EventSummary event = EventPublication.from(draft, UUID.randomUUID(), "Alice Khan", NOW);
        publicationStore.publish(draft.id(), event, capacity);
        return event.id();
    }

    private int reservedCount(UUID eventId) {
        Integer reserved = jdbc.queryForObject(
                "select reserved_count from events where id = :id",
                new MapSqlParameterSource().addValue("id", eventId),
                Integer.class);
        return reserved == null ? 0 : reserved;
    }

    private long countEvents() {
        Long count = jdbc.getJdbcTemplate().queryForObject("select count(*) from events", Long.class);
        return count == null ? 0L : count;
    }
}
