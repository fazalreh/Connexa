package com.connexa.mobile.feature.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.attendance.EventCapacity;
import com.connexa.mobile.core.attendance.RsvpStatus;
import com.connexa.mobile.core.attendance.WaitlistPlace;
import java.util.UUID;
import org.junit.Test;

public class SeatAvailabilityViewTest {

    private static final UUID EVENT = UUID.randomUUID();

    private static EventCapacity bounded(int total, int reserved) {
        int remaining = Math.max(0, total - reserved);
        return new EventCapacity(EVENT, total, remaining, reserved, remaining == 0);
    }

    private static EventCapacity unbounded() {
        return new EventCapacity(EVENT, null, null, 7, false);
    }

    @Test
    public void showsRemainingSeats() {
        SeatAvailabilityView view =
                SeatAvailabilityView.from(bounded(30, 12), null, WaitlistPlace.notWaiting());

        assertEquals("18 seats left", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.NONE, view.getAction());
        assertTrue(view.isRsvpAllowed());
    }

    @Test
    public void singularWhenOneSeatRemains() {
        SeatAvailabilityView view =
                SeatAvailabilityView.from(bounded(30, 29), null, WaitlistPlace.notWaiting());

        assertEquals("1 seat left", view.getSeatText());
    }

    @Test
    public void offersTheQueueOnlyWhenFull() {
        SeatAvailabilityView view =
                SeatAvailabilityView.from(bounded(10, 10), null, WaitlistPlace.notWaiting());

        assertEquals("This event is full", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.JOIN_WAITLIST, view.getAction());
        assertFalse(view.isRsvpAllowed());
    }

    @Test
    public void someoneHoldingTheLastSeatIsNotToldItIsFull() {
        // Their own RSVP is what filled it; "full" would read as a problem rather than
        // as confirmation that they are in.
        SeatAvailabilityView view =
                SeatAvailabilityView.from(bounded(10, 10), RsvpStatus.GOING, WaitlistPlace.notWaiting());

        assertEquals("Your seat is confirmed", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.NONE, view.getAction());
        assertTrue(view.isRsvpAllowed());
    }

    @Test
    public void queuedAttendeeSeesTheirRank() {
        SeatAvailabilityView view =
                SeatAvailabilityView.from(bounded(10, 10), null, new WaitlistPlace(3L, true));

        assertEquals("You are number 3 on the waitlist", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.LEAVE_WAITLIST, view.getAction());
    }

    @Test
    public void queuedWithoutARankStillReadsSensibly() {
        SeatAvailabilityView view =
                SeatAvailabilityView.from(bounded(10, 10), null, new WaitlistPlace(null, true));

        assertEquals("You are on the waitlist", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.LEAVE_WAITLIST, view.getAction());
    }

    @Test
    public void anUnboundedEventNeverOffersAQueue() {
        SeatAvailabilityView view =
                SeatAvailabilityView.from(unbounded(), null, WaitlistPlace.notWaiting());

        assertEquals("Open to everyone", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.NONE, view.getAction());
        assertTrue(view.isRsvpAllowed());
    }

    @Test
    public void unknownAvailabilitySaysNothingRatherThanGuessing() {
        SeatAvailabilityView view =
                SeatAvailabilityView.from(null, null, WaitlistPlace.notWaiting());

        assertEquals("", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.NONE, view.getAction());
        // RSVP stays available: refusing it because a side call failed would block a
        // working action for no reason.
        assertTrue(view.isRsvpAllowed());
    }

    @Test
    public void waitlistTakesPrecedenceOverSeatCount() {
        // Someone queued while seats were freed must still see their place, not a count
        // that invites them to bypass the queue they are already in.
        SeatAvailabilityView view =
                SeatAvailabilityView.from(bounded(10, 8), null, new WaitlistPlace(1L, true));

        assertEquals("You are number 1 on the waitlist", view.getSeatText());
        assertEquals(SeatAvailabilityView.Action.LEAVE_WAITLIST, view.getAction());
    }
}
