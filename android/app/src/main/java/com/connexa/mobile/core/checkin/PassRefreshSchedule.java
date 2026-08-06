package com.connexa.mobile.core.checkin;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides when a displayed pass should be replaced.
 *
 * <p>A pass is deliberately short-lived, so the screen showing it has to renew it before it
 * lapses. Waiting for expiry is not an option: the attendee would reach the front of the
 * queue holding a code the steward's scanner rejects, and the failure would look like theirs.
 *
 * <p>Kept apart from the screen so the timing can be tested without a device, and without
 * waiting minutes for a real pass to age.
 */
public final class PassRefreshSchedule {

    /**
     * How far ahead of expiry to renew.
     *
     * <p>Long enough to cover a slow request and a moment of queuing; short enough that most
     * of the pass's life is still spent showing one code rather than churning.
     */
    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(45);

    /** Never busy-loop, however odd the expiry the server reports. */
    private static final long MINIMUM_DELAY_MILLIS = 1_000L;

    private PassRefreshSchedule() {
    }

    /**
     * @return milliseconds to wait before asking for a new pass
     */
    public static long millisUntilRefresh(Instant expiresAt, Instant now) {
        if (expiresAt == null || now == null) {
            return MINIMUM_DELAY_MILLIS;
        }
        long untilExpiry = Duration.between(now, expiresAt).toMillis();
        // Already lapsed, or about to: renew immediately rather than showing a dead code.
        if (untilExpiry <= SAFETY_MARGIN.toMillis()) {
            return MINIMUM_DELAY_MILLIS;
        }
        return Math.max(MINIMUM_DELAY_MILLIS, untilExpiry - SAFETY_MARGIN.toMillis());
    }

    /**
     * Whole seconds of life left, floored at zero, for the countdown shown beside the code.
     *
     * <p>Attendees hold a phone up at a door and want to know whether the thing on screen is
     * still good. Showing the remaining time answers that without them having to guess.
     */
    public static long secondsRemaining(Instant expiresAt, Instant now) {
        if (expiresAt == null || now == null) {
            return 0L;
        }
        // getSeconds rather than toSeconds: the latter arrived in API 31 and this
        // application supports 26, where it would throw at the door.
        return Math.max(0L, Duration.between(now, expiresAt).getSeconds());
    }
}
