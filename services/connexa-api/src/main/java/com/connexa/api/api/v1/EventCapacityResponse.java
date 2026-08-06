package com.connexa.api.api.v1;

import com.connexa.api.domain.event.EventCapacity;
import java.util.UUID;

/**
 * Seat availability for an event.
 *
 * <p>{@code totalCapacity} and {@code spotsRemaining} are both null when the event is
 * unbounded, which lets a client distinguish "no limit" from "no seats left".
 */
public record EventCapacityResponse(
        UUID eventId,
        Integer totalCapacity,
        Integer spotsRemaining,
        int reserved,
        boolean full) {

    public static EventCapacityResponse from(EventCapacity capacity) {
        return new EventCapacityResponse(
                capacity.eventId(),
                capacity.totalCapacity(),
                capacity.spotsRemaining(),
                capacity.reserved(),
                capacity.full());
    }
}
