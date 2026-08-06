package com.connexa.api.infrastructure.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.waitlist.WaitlistEntry;
import com.connexa.api.infrastructure.attendance.JdbcAttendanceStore;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcWaitlistStoreTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcWaitlistStore waitlist;
    private JdbcAttendanceStore attendance;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        jdbc = JdbcAdapterTestSupport.freshDatabase();
        waitlist = new JdbcWaitlistStore(jdbc);
        attendance = new JdbcAttendanceStore(jdbc, waitlist);
        eventId = JdbcAdapterTestSupport.insertEvent(
                jdbc, "Robotics Workshop", "Seat limited", STARTS, EventStatus.PUBLISHED, 1);
    }

    private static IdentityKey attendee(String name) {
        return new IdentityKey("https://identity.connexa", name);
    }

    @Test
    @DisplayName("joining assigns places in arrival order")
    void assignsPlacesInArrivalOrder() {
        waitlist.join(attendee("a"), eventId, NOW);
        waitlist.join(attendee("b"), eventId, NOW.plusSeconds(1));
        waitlist.join(attendee("c"), eventId, NOW.plusSeconds(2));

        assertThat(waitlist.waiting(eventId)).extracting(entry -> entry.identity().subject())
                .containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("place in queue is reported as a rank, not a raw position")
    void reportsRankRatherThanRawPosition() {
        waitlist.join(attendee("a"), eventId, NOW);
        waitlist.join(attendee("b"), eventId, NOW.plusSeconds(1));

        assertThat(waitlist.placeInQueue(attendee("a"), eventId)).contains(1L);
        assertThat(waitlist.placeInQueue(attendee("b"), eventId)).contains(2L);
    }

    @Test
    @DisplayName("promotion advances everyone behind the promoted attendee")
    void rankAdvancesAfterPromotion() {
        // Someone holds the only seat, and two people queue behind them.
        attendance.setRsvp(attendee("holder"), eventId, RsvpStatus.GOING, NOW);
        waitlist.join(attendee("a"), eventId, NOW);
        waitlist.join(attendee("b"), eventId, NOW.plusSeconds(1));

        attendance.setRsvp(attendee("holder"), eventId, null, NOW.plusSeconds(10));

        // "a" took the seat, so "b" is now first in line rather than still second.
        assertThat(waitlist.placeInQueue(attendee("b"), eventId)).contains(1L);
    }

    @Test
    @DisplayName("joining twice keeps the original place")
    void rejoiningKeepsOriginalPlace() {
        WaitlistEntry first = waitlist.join(attendee("a"), eventId, NOW);
        waitlist.join(attendee("b"), eventId, NOW.plusSeconds(1));

        WaitlistEntry again = waitlist.join(attendee("a"), eventId, NOW.plusSeconds(2));

        // Re-joining must not move someone to the back of a queue they already hold.
        assertThat(again.position()).isEqualTo(first.position());
        assertThat(waitlist.placeInQueue(attendee("a"), eventId)).contains(1L);
    }

    @Test
    @DisplayName("a cancelled seat transfers to the longest waiting attendee")
    void cancellationPromotesTheFirstInQueue() {
        attendance.setRsvp(attendee("holder"), eventId, RsvpStatus.GOING, NOW);
        waitlist.join(attendee("a"), eventId, NOW);
        waitlist.join(attendee("b"), eventId, NOW.plusSeconds(1));

        attendance.setRsvp(attendee("holder"), eventId, null, NOW.plusSeconds(10));

        assertThat(attendance.find(attendee("a"), eventId).orElseThrow().rsvpStatus())
                .isEqualTo(RsvpStatus.GOING);
        assertThat(attendance.find(attendee("b"), eventId)).isEmpty();
    }

    @Test
    @DisplayName("the seat is never free during a transfer")
    void seatCountStaysConstantAcrossATransfer() {
        attendance.setRsvp(attendee("holder"), eventId, RsvpStatus.GOING, NOW);
        waitlist.join(attendee("a"), eventId, NOW);

        attendance.setRsvp(attendee("holder"), eventId, null, NOW.plusSeconds(10));

        // Releasing then re-taking would let a bystander grab the seat mid-way.
        assertThat(attendance.findCapacity(eventId).orElseThrow().reserved()).isEqualTo(1);
        assertThat(attendance.findCapacity(eventId).orElseThrow().full()).isTrue();
    }

    @Test
    @DisplayName("with nobody waiting the seat is simply released")
    void releasesSeatWhenQueueIsEmpty() {
        attendance.setRsvp(attendee("holder"), eventId, RsvpStatus.GOING, NOW);

        attendance.setRsvp(attendee("holder"), eventId, null, NOW.plusSeconds(10));

        assertThat(attendance.findCapacity(eventId).orElseThrow().reserved()).isZero();
    }

    @Test
    @DisplayName("a promoted attendee is not promoted a second time")
    void promotedEntryIsNotReused() {
        attendance.setRsvp(attendee("holder"), eventId, RsvpStatus.GOING, NOW);
        waitlist.join(attendee("a"), eventId, NOW);
        attendance.setRsvp(attendee("holder"), eventId, null, NOW.plusSeconds(10));

        // A second freed seat must find nobody waiting, not hand "a" a second seat.
        assertThat(waitlist.promoteNext(eventId, NOW.plusSeconds(20))).isFalse();
        assertThat(waitlist.waiting(eventId)).isEmpty();
    }

    @Test
    @DisplayName("leaving the queue forfeits the place")
    void leavingRemovesTheEntry() {
        waitlist.join(attendee("a"), eventId, NOW);
        waitlist.join(attendee("b"), eventId, NOW.plusSeconds(1));

        assertThat(waitlist.leave(attendee("a"), eventId)).isTrue();

        assertThat(waitlist.waiting(eventId)).extracting(entry -> entry.identity().subject())
                .containsExactly("b");
        assertThat(waitlist.placeInQueue(attendee("b"), eventId)).contains(1L);
    }

    @Test
    @DisplayName("leaving when not queued reports nothing removed")
    void leavingWithoutAnEntryIsHarmless() {
        assertThat(waitlist.leave(attendee("nobody"), eventId)).isFalse();
    }

    @Test
    @DisplayName("a promotion preserves an existing saved marker")
    void promotionPreservesSavedMarker() {
        attendance.setRsvp(attendee("holder"), eventId, RsvpStatus.GOING, NOW);
        attendance.setSaved(attendee("a"), eventId, true, NOW);
        waitlist.join(attendee("a"), eventId, NOW);

        attendance.setRsvp(attendee("holder"), eventId, null, NOW.plusSeconds(10));

        assertThat(attendance.find(attendee("a"), eventId).orElseThrow().saved()).isTrue();
    }

    @Test
    @DisplayName("queues are independent per event")
    void queuesAreScopedToAnEvent() {
        UUID other = JdbcAdapterTestSupport.insertEvent(
                jdbc, "Career Fair", "Also full", STARTS, EventStatus.PUBLISHED, 1);
        waitlist.join(attendee("a"), eventId, NOW);

        assertThat(waitlist.waiting(other)).isEmpty();
        assertThat(waitlist.placeInQueue(attendee("a"), other)).isEmpty();
    }
}
