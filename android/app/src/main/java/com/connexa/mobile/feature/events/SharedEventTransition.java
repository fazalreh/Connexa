package com.connexa.mobile.feature.events;

import java.util.Objects;
import java.util.UUID;

/**
 * The name that ties a card in the feed to the screen it opens.
 *
 * <p>A shared element is matched by name across two activities, so the feed and the detail
 * screen have to agree on a string neither can see the other produce. Putting that agreement
 * in one function is what stops the two sides drifting apart — a mismatch does not crash or
 * warn, it simply stops animating, which is the kind of failure nobody notices until the
 * screen already feels cheap.
 *
 * <p>The name carries the event id because a list has many cards on screen at once and the
 * framework needs to know which one the new screen grew out of. A constant name would leave
 * it animating from whichever card it happened to find first.
 */
public final class SharedEventTransition {

    private static final String TITLE_PREFIX = "connexa:event-title:";

    private SharedEventTransition() {
    }

    public static String titleName(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId is required");
        return TITLE_PREFIX + eventId;
    }
}
