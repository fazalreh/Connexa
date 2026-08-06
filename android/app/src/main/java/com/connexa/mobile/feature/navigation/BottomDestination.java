package com.connexa.mobile.feature.navigation;

import android.app.Activity;
import androidx.annotation.IdRes;
import com.connexa.mobile.R;
import com.connexa.mobile.feature.assistant.AssistantActivity;
import com.connexa.mobile.feature.calendar.CalendarActivity;
import com.connexa.mobile.feature.events.EventFeedActivity;
import com.connexa.mobile.feature.notifications.NotificationInboxActivity;

/**
 * The top level of the app.
 *
 * <p>Four destinations, because a bar of five or more shrinks each label to the point where
 * it is read as an icon anyway, and anything below three is not worth a permanent bar.
 *
 * <p>Everything else — organizer tools, account, an individual event — is reached from inside
 * one of these rather than given a permanent seat. A bar is a statement about what the app is
 * for, and adding to it dilutes that.
 */
public enum BottomDestination {

    DISCOVER(R.id.destination_discover, EventFeedActivity.class),
    CALENDAR(R.id.destination_calendar, CalendarActivity.class),
    ASSISTANT(R.id.destination_assistant, AssistantActivity.class),
    ALERTS(R.id.destination_alerts, NotificationInboxActivity.class);

    private final int menuItemId;
    private final Class<? extends Activity> activity;

    BottomDestination(@IdRes int menuItemId, Class<? extends Activity> activity) {
        this.menuItemId = menuItemId;
        this.activity = activity;
    }

    @IdRes
    public int getMenuItemId() {
        return menuItemId;
    }

    public Class<? extends Activity> getActivity() {
        return activity;
    }

    /** @return the destination that owns this menu entry, or null if none does */
    public static BottomDestination forMenuItem(@IdRes int menuItemId) {
        for (BottomDestination destination : values()) {
            if (destination.menuItemId == menuItemId) {
                return destination;
            }
        }
        return null;
    }

    /** @return the destination this screen belongs to, or null for a screen outside the bar */
    public static BottomDestination forActivity(Class<?> activityClass) {
        for (BottomDestination destination : values()) {
            if (destination.activity.equals(activityClass)) {
                return destination;
            }
        }
        return null;
    }
}
