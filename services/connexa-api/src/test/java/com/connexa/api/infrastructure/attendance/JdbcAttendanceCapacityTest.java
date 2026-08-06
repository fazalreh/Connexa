package com.connexa.api.infrastructure.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.connexa.api.domain.attendance.EventAtCapacityException;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventCapacity;
import com.connexa.api.domain.event.EventNotFoundException;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcAttendanceCapacityTest {

    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");
    private static final IdentityKey BOB = new IdentityKey("https://identity.connexa", "bob");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcAttendanceStore store;

    @BeforeEach
    void setUp() {
        jdbc = JdbcAdapterTestSupport.freshDatabase();
        store = new JdbcAttendanceStore(jdbc);
    }

    @Test
    @DisplayName("a GOING response takes a seat")
    void goingReservesASeat() {
        UUID eventId = event(10);

        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);

        EventCapacity capacity = store.findCapacity(eventId).orElseThrow();
        assertThat(capacity.reserved()).isEqualTo(1);
        assertThat(capacity.spotsRemaining()).isEqualTo(9);
        assertThat(capacity.full()).isFalse();
    }

    @Test
    @DisplayName("repeating a GOING response does not take a second seat")
    void repeatedGoingIsIdempotent() {
        UUID eventId = event(10);

        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW.plusSeconds(60));
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW.plusSeconds(120));

        assertThat(store.findCapacity(eventId).orElseThrow().reserved()).isEqualTo(1);
    }

    @Test
    @DisplayName("INTERESTED and DECLINED do not take a seat")
    void nonAttendingResponsesDoNotReserve() {
        UUID eventId = event(10);

        store.setRsvp(ALICE, eventId, RsvpStatus.INTERESTED, NOW);
        store.setRsvp(BOB, eventId, RsvpStatus.DECLINED, NOW);

        assertThat(store.findCapacity(eventId).orElseThrow().reserved()).isZero();
    }

    @Test
    @DisplayName("moving away from GOING returns the seat")
    void changingAwayFromGoingReleasesTheSeat() {
        UUID eventId = event(10);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);

        store.setRsvp(ALICE, eventId, RsvpStatus.INTERESTED, NOW.plusSeconds(60));

        assertThat(store.findCapacity(eventId).orElseThrow().reserved()).isZero();
    }

    @Test
    @DisplayName("clearing an RSVP returns the seat")
    void clearingRsvpReleasesTheSeat() {
        UUID eventId = event(10);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);

        store.setRsvp(ALICE, eventId, null, NOW.plusSeconds(60));

        assertThat(store.findCapacity(eventId).orElseThrow().reserved()).isZero();
    }

    @Test
    @DisplayName("a full event refuses a further GOING response")
    void refusesToOverbook() {
        UUID eventId = event(1);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);

        assertThatThrownBy(() -> store.setRsvp(BOB, eventId, RsvpStatus.GOING, NOW))
                .isInstanceOf(EventAtCapacityException.class);

        assertThat(store.findCapacity(eventId).orElseThrow().reserved()).isEqualTo(1);
    }

    @Test
    @DisplayName("a rejected reservation records no attendance")
    void rejectedReservationLeavesNoAttendanceRow() {
        UUID eventId = event(1);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);

        assertThatThrownBy(() -> store.setRsvp(BOB, eventId, RsvpStatus.GOING, NOW))
                .isInstanceOf(EventAtCapacityException.class);

        assertThat(store.find(BOB, eventId)).isEmpty();
    }

    @Test
    @DisplayName("a released seat becomes available to someone else")
    void releasedSeatCanBeTakenAgain() {
        UUID eventId = event(1);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);
        store.setRsvp(ALICE, eventId, null, NOW.plusSeconds(60));

        store.setRsvp(BOB, eventId, RsvpStatus.GOING, NOW.plusSeconds(120));

        assertThat(store.findCapacity(eventId).orElseThrow().reserved()).isEqualTo(1);
        assertThat(store.find(BOB, eventId).orElseThrow().rsvpStatus()).isEqualTo(RsvpStatus.GOING);
    }

    @Test
    @DisplayName("an event with capacity zero admits nobody")
    void zeroCapacityAdmitsNobody() {
        UUID eventId = event(0);

        assertThatThrownBy(() -> store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW))
                .isInstanceOf(EventAtCapacityException.class);
    }

    @Test
    @DisplayName("an unbounded event never refuses a response")
    void unboundedEventNeverRefuses() {
        UUID eventId = event(null);

        for (int index = 0; index < 50; index++) {
            store.setRsvp(attendee(index), eventId, RsvpStatus.GOING, NOW);
        }

        EventCapacity capacity = store.findCapacity(eventId).orElseThrow();
        assertThat(capacity.unbounded()).isTrue();
        assertThat(capacity.spotsRemaining()).isNull();
        assertThat(capacity.full()).isFalse();
        assertThat(capacity.reserved()).isEqualTo(50);
    }

    @Test
    @DisplayName("reserving against an unpublished event reports the event as missing")
    void unpublishedEventIsNotBookable() {
        UUID draftId = JdbcAdapterTestSupport.insertEvent(
                jdbc, "Hidden Draft", "Not yet public", STARTS, EventStatus.DRAFT, 10);

        assertThatThrownBy(() -> store.setRsvp(ALICE, draftId, RsvpStatus.GOING, NOW))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("reserving against an unknown event reports the event as missing")
    void unknownEventIsNotBookable() {
        assertThatThrownBy(() -> store.setRsvp(ALICE, UUID.randomUUID(), RsvpStatus.GOING, NOW))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    @DisplayName("capacity of an unknown event is empty")
    void capacityOfUnknownEventIsEmpty() {
        assertThat(store.findCapacity(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("a full event reports zero remaining")
    void fullEventReportsZeroRemaining() {
        UUID eventId = event(2);
        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW);
        store.setRsvp(BOB, eventId, RsvpStatus.GOING, NOW);

        EventCapacity capacity = store.findCapacity(eventId).orElseThrow();
        assertThat(capacity.spotsRemaining()).isZero();
        assertThat(capacity.full()).isTrue();
    }

    @Test
    @DisplayName("a seat reservation leaves an existing save untouched")
    void reservingPreservesSavedFlag() {
        UUID eventId = event(10);
        store.setSaved(ALICE, eventId, true, NOW);

        store.setRsvp(ALICE, eventId, RsvpStatus.GOING, NOW.plusSeconds(60));

        assertThat(store.find(ALICE, eventId).orElseThrow().saved()).isTrue();
    }

    @Test
    @DisplayName("concurrent attendees cannot overbook the last seats")
    void concurrentReservationsNeverExceedCapacity() throws Exception {
        int capacity = 5;
        int contenders = 40;
        UUID eventId = event(capacity);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            // Every contender uses a distinct identity, so the only contended row is the
            // event itself — exactly the race the reservation guard has to survive.
            List<Callable<Boolean>> attempts = IntStream.range(0, contenders)
                    .<Callable<Boolean>>mapToObj(index -> () -> {
                        try {
                            store.setRsvp(attendee(index), eventId, RsvpStatus.GOING, NOW);
                            return Boolean.TRUE;
                        } catch (EventAtCapacityException expected) {
                            return Boolean.FALSE;
                        }
                    })
                    .toList();

            List<Future<Boolean>> results = pool.invokeAll(attempts);
            long granted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    granted++;
                }
            }

            assertThat(granted).isEqualTo(capacity);
            assertThat(store.findCapacity(eventId).orElseThrow().reserved()).isEqualTo(capacity);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UUID event(Integer totalCapacity) {
        return JdbcAdapterTestSupport.insertEvent(
                jdbc, "Capacity Event", "Seat limited session", STARTS,
                EventStatus.PUBLISHED, totalCapacity);
    }

    private static IdentityKey attendee(int index) {
        return new IdentityKey("https://identity.connexa", "attendee-" + index);
    }
}
