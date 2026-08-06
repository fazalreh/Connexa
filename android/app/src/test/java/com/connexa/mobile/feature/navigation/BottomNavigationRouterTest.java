package com.connexa.mobile.feature.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.connexa.mobile.R;
import com.connexa.mobile.feature.assistant.AssistantActivity;
import com.connexa.mobile.feature.calendar.CalendarActivity;
import com.connexa.mobile.feature.events.EventDetailsActivity;
import com.connexa.mobile.feature.events.EventFeedActivity;
import com.connexa.mobile.feature.notifications.NotificationInboxActivity;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class BottomNavigationRouterTest {

    @Test
    public void opensTheScreenBehindATappedEntry() {
        BottomNavigationRouter router = new BottomNavigationRouter(EventFeedActivity.class);

        assertEquals(CalendarActivity.class, router.resolve(R.id.destination_calendar));
        assertEquals(AssistantActivity.class, router.resolve(R.id.destination_assistant));
        assertEquals(NotificationInboxActivity.class, router.resolve(R.id.destination_alerts));
    }

    @Test
    public void reTappingTheCurrentTabDoesNothing() {
        // Relaunching would discard scroll position and anything already typed, which
        // reads as the app throwing away work.
        BottomNavigationRouter router = new BottomNavigationRouter(EventFeedActivity.class);

        assertNull(router.resolve(R.id.destination_discover));
    }

    @Test
    public void highlightsTheEntryForTheCurrentScreen() {
        assertEquals(
                R.id.destination_calendar,
                new BottomNavigationRouter(CalendarActivity.class).selectedMenuItemId());
    }

    @Test
    public void aScreenOutsideTheBarHighlightsNothing() {
        // An event detail is reached from a tab but is not one. Highlighting the tab it
        // came from would claim the bar leads back there, and it does not.
        assertEquals(0, new BottomNavigationRouter(EventDetailsActivity.class).selectedMenuItemId());
    }

    @Test
    public void anUnknownEntryIsIgnored() {
        assertNull(new BottomNavigationRouter(EventFeedActivity.class).resolve(R.id.open_events_button));
    }

    @Test
    public void everyDestinationHasItsOwnEntryAndScreen() {
        // A duplicated id would leave one tab permanently unreachable, and the bar would
        // still look correct, so nothing else would catch it.
        Set<Integer> menuItemIds = new HashSet<>();
        Set<Class<?>> activities = new HashSet<>();
        for (BottomDestination destination : BottomDestination.values()) {
            menuItemIds.add(destination.getMenuItemId());
            activities.add(destination.getActivity());
        }

        assertEquals(BottomDestination.values().length, menuItemIds.size());
        assertEquals(BottomDestination.values().length, activities.size());
        assertNotEquals(0, BottomDestination.values().length);
    }

    @Test
    public void everyEntryResolvesBackToItsOwnDestination() {
        for (BottomDestination destination : BottomDestination.values()) {
            assertEquals(destination, BottomDestination.forMenuItem(destination.getMenuItemId()));
            assertEquals(destination, BottomDestination.forActivity(destination.getActivity()));
        }
    }
}
