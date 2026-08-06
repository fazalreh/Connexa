package com.connexa.api.infrastructure.waitlist;

import java.time.Instant;
import java.util.UUID;

/**
 * Hands a freed seat to the next attendee in the queue.
 *
 * <p>Kept as its own interface so the attendance store can call it without depending on the
 * waitlist implementation, and so a deployment with no waitlist keeps the plain
 * release-the-seat behaviour rather than needing a stub table.
 */
public interface WaitlistPromoter {

    /**
     * Promotes the longest-waiting attendee into a seat that is being given up.
     *
     * <p>Called while the releasing attendee's transaction is still open, so the seat is
     * transferred rather than released and re-taken. If it were released first, anyone
     * refreshing the page could take it ahead of the person who has been waiting.
     *
     * @return true when someone was promoted and the seat should stay reserved
     */
    boolean promoteNext(UUID eventId, Instant at);

    /** A deployment without a waitlist simply frees the seat. */
    WaitlistPromoter NONE = (eventId, at) -> false;
}
