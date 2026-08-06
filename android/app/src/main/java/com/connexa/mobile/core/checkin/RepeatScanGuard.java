package com.connexa.mobile.core.checkin;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Stops one code in front of the lens from being redeemed over and over.
 *
 * <p>A continuous decoder reports the same square many times a second for as long as it stays
 * in view, and an attendee holding a phone up does not whisk it away the instant it beeps.
 * Without this the door would fire a redemption per frame: the first succeeds, the rest come
 * back as duplicates, and the steward sees a burst of "already checked in" for someone who
 * has only just arrived.
 *
 * <p>A different code is always accepted immediately. Queues move fast, and making the next
 * guest wait out a timer that exists for the previous one would be the wrong trade.
 */
public final class RepeatScanGuard {

    /**
     * How long the same code stays ignored.
     *
     * <p>Comfortably longer than it takes to lower a phone and raise the next one, short
     * enough that a genuine rescan — a steward checking a code that failed the first time —
     * is not left waiting.
     */
    private static final Duration QUIET_PERIOD = Duration.ofSeconds(5);

    private String lastAccepted;
    private Instant acceptedAt;

    /**
     * @return true when this scan should be acted on
     */
    public boolean shouldHandle(String scanned, Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (scanned == null || scanned.isBlank()) {
            return false;
        }
        if (scanned.equals(lastAccepted)
                && acceptedAt != null
                && Duration.between(acceptedAt, now).compareTo(QUIET_PERIOD) < 0) {
            return false;
        }
        lastAccepted = scanned;
        acceptedAt = now;
        return true;
    }

    /**
     * Forgets the last code, so an immediate rescan is honoured.
     *
     * <p>Used when a scan failed for a reason the steward can fix — a lapsed pass the guest
     * refreshes, a lost connection. Making them wait out the quiet period after being told to
     * try again would be its own small insult.
     */
    public void reset() {
        lastAccepted = null;
        acceptedAt = null;
    }
}
