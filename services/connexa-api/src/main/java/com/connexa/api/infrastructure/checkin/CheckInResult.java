package com.connexa.api.infrastructure.checkin;

import java.time.Instant;
import java.util.Objects;

/**
 * @param firstTime false when the attendee had already been checked in, in which case
 *     {@code arrivedAt} is their original arrival rather than now
 */
public record CheckInResult(Instant arrivedAt, boolean firstTime) {

    public CheckInResult {
        Objects.requireNonNull(arrivedAt, "arrivedAt is required");
    }
}
