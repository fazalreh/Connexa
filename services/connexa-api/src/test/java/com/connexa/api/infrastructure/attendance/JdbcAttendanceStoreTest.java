package com.connexa.api.infrastructure.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcAttendanceStoreTest {

    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");
    private static final IdentityKey BOB = new IdentityKey("https://identity.connexa", "bob");
    private static final Instant FIRST = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-06-02T10:00:00Z");
    private static final Instant THIRD = Instant.parse("2026-06-03T10:00:00Z");

    private JdbcAttendanceStore store;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        NamedParameterJdbcTemplate jdbc = JdbcAdapterTestSupport.freshDatabase();
        store = new JdbcAttendanceStore(jdbc);
        // A GOING response reserves a seat, so these tests need a real published event to
        // reserve against. It is left unbounded; capacity limits are covered separately.
        eventId = JdbcAdapterTestSupport.insertEvent(
                jdbc,
                "Community Meetup",
                "Open to everyone",
                Instant.parse("2026-07-01T10:00:00Z"),
                EventStatus.PUBLISHED,
                null);
    }

    @Test
    @DisplayName("saving an event persists across a fresh read")
    void persistsSavedState() {
        store.setSaved(ALICE, eventId, true, FIRST);

        AttendanceState stored = store.find(ALICE, eventId).orElseThrow();

        assertThat(stored.eventId()).isEqualTo(eventId);
        assertThat(stored.saved()).isTrue();
        assertThat(stored.rsvpStatus()).isNull();
        assertThat(stored.updatedAt()).isEqualTo(FIRST);
    }

    @Test
    @DisplayName("responding to an event keeps an existing save")
    void rsvpPreservesSavedFlag() {
        store.setSaved(ALICE, eventId, true, FIRST);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, SECOND);

        AttendanceState stored = store.find(ALICE, eventId).orElseThrow();

        assertThat(stored.saved()).isTrue();
        assertThat(stored.rsvpStatus()).isEqualTo(RsvpStatus.GOING);
        assertThat(stored.updatedAt()).isEqualTo(SECOND);
    }

    @Test
    @DisplayName("un-saving an event keeps an existing response")
    void unsavingPreservesRsvp() {
        store.setRsvp(ALICE, eventId, RsvpStatus.INTERESTED, FIRST);
        store.setSaved(ALICE, eventId, false, SECOND);

        AttendanceState stored = store.find(ALICE, eventId).orElseThrow();

        assertThat(stored.saved()).isFalse();
        assertThat(stored.rsvpStatus()).isEqualTo(RsvpStatus.INTERESTED);
        assertThat(stored.updatedAt()).isEqualTo(SECOND);
    }

    @Test
    @DisplayName("an RSVP can be changed in place")
    void replacesRsvpStatus() {
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, FIRST);
        store.setRsvp(ALICE, eventId, RsvpStatus.DECLINED, SECOND);

        assertThat(store.find(ALICE, eventId).orElseThrow().rsvpStatus())
                .isEqualTo(RsvpStatus.DECLINED);
    }

    @Test
    @DisplayName("clearing both save and RSVP leaves no attendance state")
    void clearingAllStateHidesTheRow() {
        store.setSaved(ALICE, eventId, true, FIRST);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, SECOND);

        store.setRsvp(ALICE, eventId, null, THIRD);
        store.setSaved(ALICE, eventId, false, THIRD);

        assertThat(store.find(ALICE, eventId)).isEmpty();
        assertThat(store.findAll(ALICE)).isEmpty();
    }

    @Test
    @DisplayName("state can be re-established after being cleared")
    void reestablishesStateAfterClearing() {
        store.setSaved(ALICE, eventId, true, FIRST);
        store.setSaved(ALICE, eventId, false, SECOND);
        store.setSaved(ALICE, eventId, true, THIRD);

        AttendanceState stored = store.find(ALICE, eventId).orElseThrow();

        assertThat(stored.saved()).isTrue();
        assertThat(stored.updatedAt()).isEqualTo(THIRD);
    }

    @Test
    @DisplayName("listing returns most recently updated first")
    void listsMostRecentlyUpdatedFirst() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        store.setSaved(ALICE, older, true, FIRST);
        store.setSaved(ALICE, newer, true, SECOND);

        List<AttendanceState> all = store.findAll(ALICE);

        assertThat(all).extracting(AttendanceState::eventId).containsExactly(newer, older);
    }

    @Test
    @DisplayName("listing omits events whose state was cleared")
    void listingOmitsClearedState() {
        UUID kept = UUID.randomUUID();
        UUID cleared = UUID.randomUUID();
        store.setSaved(ALICE, kept, true, FIRST);
        store.setSaved(ALICE, cleared, true, FIRST);
        store.setSaved(ALICE, cleared, false, SECOND);

        assertThat(store.findAll(ALICE)).extracting(AttendanceState::eventId).containsExactly(kept);
    }

    @Test
    @DisplayName("one identity never sees another identity's attendance")
    void isolatesStateByIdentity() {
        store.setSaved(ALICE, eventId, true, FIRST);
        store.setRsvp(BOB, eventId, RsvpStatus.DECLINED, SECOND);

        assertThat(store.find(ALICE, eventId).orElseThrow().rsvpStatus()).isNull();
        assertThat(store.find(BOB, eventId).orElseThrow().saved()).isFalse();
        assertThat(store.findAll(ALICE)).hasSize(1);
        assertThat(store.findAll(BOB)).hasSize(1);
    }

    @Test
    @DisplayName("an identity with no attendance reads as empty")
    void readsEmptyForUnknownIdentity() {
        assertThat(store.find(ALICE, eventId)).isEmpty();
        assertThat(store.findAll(ALICE)).isEmpty();
    }
}
