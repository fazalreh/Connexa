package com.connexa.mobile.feature.events;

import com.connexa.mobile.core.attendance.EventCapacity;
import com.connexa.mobile.core.attendance.RsvpStatus;
import com.connexa.mobile.core.attendance.WaitlistPlace;
import java.util.Objects;

/**
 * Decides what an attendee is told about seats, and which queue action is offered.
 *
 * <p>Kept free of Android types so the rules can be tested directly. The combinations here
 * are easy to get subtly wrong — offering a queue to someone who already holds a seat, or
 * showing "full" to the person occupying the last one — and each mistake is the kind a user
 * notices immediately.
 */
public final class SeatAvailabilityView {

    /** Which queue control, if any, the screen should present. */
    public enum Action {
        NONE,
        JOIN_WAITLIST,
        LEAVE_WAITLIST
    }

    private final String seatText;
    private final Action action;
    private final boolean rsvpAllowed;

    private SeatAvailabilityView(String seatText, Action action, boolean rsvpAllowed) {
        this.seatText = seatText;
        this.action = action;
        this.rsvpAllowed = rsvpAllowed;
    }

    public String getSeatText() {
        return seatText;
    }

    public Action getAction() {
        return action;
    }

    /** False when the event is full and the caller does not already hold a seat. */
    public boolean isRsvpAllowed() {
        return rsvpAllowed;
    }

    /**
     * @param capacity may be null when availability could not be read; the screen then says
     *     nothing about seats rather than guessing
     */
    public static SeatAvailabilityView from(
            EventCapacity capacity, RsvpStatus rsvpStatus, WaitlistPlace place) {
        Objects.requireNonNull(place, "place is required");

        if (capacity == null) {
            return new SeatAvailabilityView("", Action.NONE, true);
        }
        if (capacity.isUnbounded()) {
            // No limit means no scarcity to report and never a queue.
            return new SeatAvailabilityView("Open to everyone", Action.NONE, true);
        }

        boolean holdsSeat = rsvpStatus == RsvpStatus.GOING;
        Integer remaining = capacity.getSpotsRemaining();
        int left = remaining == null ? 0 : remaining;

        if (place.waiting()) {
            Long rank = place.placeInQueue();
            String text = rank == null
                    ? "You are on the waitlist"
                    : "You are number " + rank + " on the waitlist";
            return new SeatAvailabilityView(text, Action.LEAVE_WAITLIST, false);
        }

        if (holdsSeat) {
            // Their own seat is counted in the total, so "full" would read as a problem
            // rather than as confirmation that they are in.
            return new SeatAvailabilityView("Your seat is confirmed", Action.NONE, true);
        }

        if (capacity.isFull()) {
            return new SeatAvailabilityView("This event is full", Action.JOIN_WAITLIST, false);
        }

        String text = left == 1 ? "1 seat left" : left + " seats left";
        return new SeatAvailabilityView(text, Action.NONE, true);
    }
}
