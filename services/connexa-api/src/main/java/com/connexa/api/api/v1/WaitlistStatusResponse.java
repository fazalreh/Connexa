package com.connexa.api.api.v1;

import com.connexa.api.domain.waitlist.WaitlistEntry;
import java.time.Instant;
import java.util.UUID;

/**
 * @param placeInQueue rank among those still waiting, so "3rd in line" stays truthful after
 *     people ahead are promoted; null once the caller has been given a seat
 * @param promoted true when a seat has already been handed to this entry
 */
public record WaitlistStatusResponse(
        UUID eventId, Long placeInQueue, boolean promoted, Instant joinedAt) {

    public static WaitlistStatusResponse from(WaitlistEntry entry, Long placeInQueue) {
        return new WaitlistStatusResponse(
                entry.eventId(), placeInQueue, !entry.waiting(), entry.joinedAt());
    }
}
