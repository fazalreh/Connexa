package com.connexa.api.infrastructure.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.infrastructure.attendance.JdbcAttendanceStore;
import com.connexa.api.infrastructure.event.JdbcEventCatalog;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcIngestedEventStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcIngestedEventStore store;
    private JdbcEventCatalog catalog;

    @BeforeEach
    void setUp() {
        jdbc = JdbcAdapterTestSupport.freshDatabase();
        catalog = new JdbcEventCatalog(jdbc);
        store = new JdbcIngestedEventStore(jdbc, catalog);
    }

    @Test
    @DisplayName("a new announcement creates a discoverable event")
    void createsEventForNewAnnouncement() {
        IngestionOutcome outcome = store.storeOnce(
                "imap", "<a@lums.edu.pk>", "hash-1", event("Robotics Workshop"), 120);

        assertThat(outcome.created()).isTrue();
        assertThat(catalog.findById(outcome.event().id())).isPresent();
        assertThat(catalog.findPublished(new EventQuery(null, null, null, 0, 20)).total())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("resubmitting the same announcement does not create a second event")
    void repeatSubmissionIsIdempotent() {
        IngestionOutcome first = store.storeOnce(
                "imap", "<a@lums.edu.pk>", "hash-1", event("Robotics Workshop"), 120);

        IngestionOutcome second = store.storeOnce(
                "imap", "<a@lums.edu.pk>", "hash-1", event("Robotics Workshop"), 120);

        assertThat(second.created()).isFalse();
        assertThat(second.event().id()).isEqualTo(first.event().id());
        assertThat(catalog.findPublished(new EventQuery(null, null, null, 0, 20)).total())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("a repeat returns the stored event, not the resubmitted one")
    void repeatReturnsTheStoredEvent() {
        IngestionOutcome first = store.storeOnce(
                "imap", "<a@lums.edu.pk>", "hash-1", event("Original Title"), 120);

        IngestionOutcome second = store.storeOnce(
                "imap", "<a@lums.edu.pk>", "hash-2", event("Edited Title"), 120);

        // The catalog is the source of truth; a later read of the same announcement
        // must not appear to have changed a published listing.
        assertThat(second.event().title()).isEqualTo("Original Title");
        assertThat(second.event().id()).isEqualTo(first.event().id());
    }

    @Test
    @DisplayName("different announcements create separate events")
    void distinctAnnouncementsCreateDistinctEvents() {
        store.storeOnce("imap", "<a@lums.edu.pk>", "h1", event("First"), 50);
        store.storeOnce("imap", "<b@lums.edu.pk>", "h2", event("Second"), 50);

        assertThat(catalog.findPublished(new EventQuery(null, null, null, 0, 20)).total())
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("the same record id from a different source is a different announcement")
    void sourceSystemIsPartOfIdentity() {
        store.storeOnce("imap", "shared-id", "h1", event("From Mail"), 50);
        IngestionOutcome other = store.storeOnce("rss", "shared-id", "h2", event("From Feed"), 50);

        assertThat(other.created()).isTrue();
        assertThat(catalog.findPublished(new EventQuery(null, null, null, 0, 20)).total())
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("an ingested event carries its capacity through to RSVP")
    void capacityIsStored() {
        IngestionOutcome outcome = store.storeOnce(
                "imap", "<a@lums.edu.pk>", "h1", event("Robotics Workshop"), 30);

        JdbcAttendanceStore attendance = new JdbcAttendanceStore(jdbc);
        assertThat(attendance.findCapacity(outcome.event().id()).orElseThrow().totalCapacity())
                .isEqualTo(30);
    }

    @Test
    @DisplayName("an announcement with no stated capacity is unbounded")
    void missingCapacityIsUnbounded() {
        IngestionOutcome outcome = store.storeOnce(
                "imap", "<a@lums.edu.pk>", "h1", event("Open Lecture"), null);

        JdbcAttendanceStore attendance = new JdbcAttendanceStore(jdbc);
        assertThat(attendance.findCapacity(outcome.event().id()).orElseThrow().unbounded())
                .isTrue();
    }

    private static EventSummary event(String title) {
        return new EventSummary(
                UUID.randomUUID(),
                title,
                "Summary for " + title,
                STARTS,
                STARTS.plusSeconds(7_200),
                "Asia/Karachi",
                "Main Auditorium",
                "Technology",
                "Campus Announcements",
                EventStatus.PUBLISHED,
                0L,
                NOW,
                NOW);
    }
}
