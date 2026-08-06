package com.connexa.mobile.core.checkin;

import java.time.Instant;
import java.util.Objects;

/**
 * What happened when a steward scanned someone in.
 *
 * @param alreadyCheckedIn true when this guest had been scanned before, in which case
 *     {@code arrivedAt} is their original arrival rather than now. A duplicate is not a
 *     failure, but a steward has to be able to tell the two apart.
 */
public record CheckInOutcome(Instant arrivedAt, boolean alreadyCheckedIn) {

    public CheckInOutcome {
        Objects.requireNonNull(arrivedAt, "arrivedAt is required");
    }
}
