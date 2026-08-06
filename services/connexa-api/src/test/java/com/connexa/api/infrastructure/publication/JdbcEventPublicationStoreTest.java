package com.connexa.api.infrastructure.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.DraftAlreadyPublishedException;
import com.connexa.api.domain.organizer.EventPublication;
import com.connexa.api.domain.organizer.OrganizerDraftNotFoundException;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import com.connexa.api.infrastructure.attendance.JdbcAttendanceStore;
import com.connexa.api.infrastructure.event.JdbcEventCatalog;
import com.connexa.api.infrastructure.organizer.JdbcOrganizerEventDraftStore;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcEventPublicationStoreTest {

    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");
    private static final Instant CREATED = Instant.parse("2026-06-01T09:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-15T12:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcEventPublicationStore publicationStore;
    private JdbcOrganizerEventDraftStore draftStore;
    private JdbcEventCatalog catalog;

    @BeforeEach
    void setUp() {
        jdbc = JdbcAdapterTestSupport.freshDatabase();
        publicationStore = new JdbcEventPublicationStore(jdbc);
        draftStore = new JdbcOrganizerEventDraftStore(jdbc);
        catalog = new JdbcEventCatalog(jdbc);
    }

    @Test
    @DisplayName("a published draft becomes discoverable in the public catalog")
    void publishedDraftAppearsInTheCatalog() {
        OrganizerEventDraft draft = savedDraft(120);
        EventSummary event = eventFor(draft);

        publicationStore.publish(draft.id(), event, draft.capacity());

        assertThat(catalog.findById(event.id())).isPresent();
        assertThat(catalog.findPublished(new EventQuery(null, null, null, 0, 20)).items())
                .extracting(EventSummary::title)
                .containsExactly("Robotics Workshop");
    }

    @Test
    @DisplayName("the published event keeps the draft's details")
    void publishedEventKeepsDraftDetails() {
        OrganizerEventDraft draft = savedDraft(120);
        EventSummary event = eventFor(draft);

        publicationStore.publish(draft.id(), event, draft.capacity());
        EventSummary stored = catalog.findById(event.id()).orElseThrow();

        assertThat(stored.title()).isEqualTo(draft.title());
        assertThat(stored.venueName()).isEqualTo(draft.location());
        assertThat(stored.category()).isEqualTo(draft.category());
        assertThat(stored.timeZone()).isEqualTo(draft.timeZone());
        assertThat(stored.startsAt()).isEqualTo(draft.startsAt());
        assertThat(stored.endsAt()).isEqualTo(draft.endsAt());
        assertThat(stored.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("the draft's capacity becomes the event's seat limit")
    void draftCapacityBecomesSeatLimit() {
        OrganizerEventDraft draft = savedDraft(120);
        EventSummary event = eventFor(draft);

        publicationStore.publish(draft.id(), event, draft.capacity());

        JdbcAttendanceStore attendance = new JdbcAttendanceStore(jdbc);
        assertThat(attendance.findCapacity(event.id()).orElseThrow().totalCapacity()).isEqualTo(120);
        assertThat(attendance.findCapacity(event.id()).orElseThrow().spotsRemaining()).isEqualTo(120);
    }

    @Test
    @DisplayName("publishing records the link back to the draft")
    void publishingRecordsTheLink() {
        OrganizerEventDraft draft = savedDraft(50);
        EventSummary event = eventFor(draft);

        publicationStore.publish(draft.id(), event, draft.capacity());

        assertThat(publicationStore.findPublishedEventId(draft.id())).contains(event.id());
    }

    @Test
    @DisplayName("an unpublished draft has no linked event")
    void unpublishedDraftHasNoLink() {
        OrganizerEventDraft draft = savedDraft(50);

        assertThat(publicationStore.findPublishedEventId(draft.id())).isEmpty();
    }

    @Test
    @DisplayName("publishing the same draft twice is refused")
    void refusesToPublishTwice() {
        OrganizerEventDraft draft = savedDraft(50);
        EventSummary first = eventFor(draft);
        publicationStore.publish(draft.id(), first, draft.capacity());

        EventSummary second = eventFor(draft);
        assertThatThrownBy(() -> publicationStore.publish(draft.id(), second, draft.capacity()))
                .isInstanceOf(DraftAlreadyPublishedException.class);

        // The conflict names the event that already exists, so a client can follow the link
        // rather than having to search for it.
        assertThat(publicationStore.findPublishedEventId(draft.id())).contains(first.id());
    }

    @Test
    @DisplayName("a refused second publish does not add a second listing")
    void refusedRepublishLeavesOneListing() {
        OrganizerEventDraft draft = savedDraft(50);
        publicationStore.publish(draft.id(), eventFor(draft), draft.capacity());

        EventSummary second = eventFor(draft);
        assertThatThrownBy(() -> publicationStore.publish(draft.id(), second, draft.capacity()))
                .isInstanceOf(DraftAlreadyPublishedException.class);

        assertThat(catalog.findPublished(new EventQuery(null, null, null, 0, 20)).total()).isEqualTo(1L);
    }

    @Test
    @DisplayName("publishing an unknown draft reports the draft as missing")
    void refusesUnknownDraft() {
        UUID unknownDraftId = UUID.randomUUID();
        EventSummary event = eventFor(savedDraft(50));

        assertThatThrownBy(() -> publicationStore.publish(unknownDraftId, event, 50))
                .isInstanceOf(OrganizerDraftNotFoundException.class);
    }

    @Test
    @DisplayName("an unbounded draft capacity publishes an unbounded event")
    void publishesUnboundedEvent() {
        OrganizerEventDraft draft = savedDraft(10);
        EventSummary event = eventFor(draft);

        publicationStore.publish(draft.id(), event, null);

        JdbcAttendanceStore attendance = new JdbcAttendanceStore(jdbc);
        assertThat(attendance.findCapacity(event.id()).orElseThrow().unbounded()).isTrue();
    }

    private OrganizerEventDraft savedDraft(int capacity) {
        OrganizerEventDraft draft = new OrganizerEventDraft(
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
                CREATED,
                CREATED);
        return draftStore.save(draft);
    }

    private static EventSummary eventFor(OrganizerEventDraft draft) {
        return EventPublication.from(draft, UUID.randomUUID(), "Alice Khan", PUBLISHED_AT);
    }
}
