package com.connexa.mobile.feature.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

/**
 * The feed and the detail screen have to produce the same name for the same event without
 * being able to see each other do it. A mismatch does not crash or warn — the transition
 * simply stops happening — so the agreement is worth pinning down.
 */
public class SharedEventTransitionTest {

    @Test
    public void bothSidesDeriveTheSameNameFromTheSameEvent() {
        UUID event = UUID.randomUUID();

        assertEquals(SharedEventTransition.titleName(event), SharedEventTransition.titleName(event));
    }

    @Test
    public void differentEventsGetDifferentNames() {
        // A list has many cards on screen at once. A shared name would leave the new screen
        // growing out of whichever card the framework matched first.
        assertNotEquals(
                SharedEventTransition.titleName(UUID.randomUUID()),
                SharedEventTransition.titleName(UUID.randomUUID()));
    }

    @Test
    public void theNameIsNamespaced() {
        // Transition names share one space across the whole window; an unqualified id could
        // collide with anything else that happens to use one.
        assertTrue(SharedEventTransition.titleName(UUID.randomUUID()).startsWith("connexa:"));
    }

    @Test
    public void theNameCarriesTheEventId() {
        UUID event = UUID.randomUUID();

        assertTrue(SharedEventTransition.titleName(event).contains(event.toString()));
    }

    @Test
    public void refusesAnEventWithNoId() {
        assertThrows(NullPointerException.class, () -> SharedEventTransition.titleName(null));
    }
}
