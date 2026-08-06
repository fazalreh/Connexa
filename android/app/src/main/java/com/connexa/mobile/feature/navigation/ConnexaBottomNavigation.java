package com.connexa.mobile.feature.navigation;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Attaches the navigation bar to a top-level screen.
 *
 * <p>These screens are separate activities, so switching between them starts one rather than
 * swapping a fragment. Two flags make that behave the way a bar is expected to:
 *
 * <ul>
 *   <li>the screen is brought forward if it already exists, so returning to a tab returns to
 *       where it was left rather than to a fresh copy;
 *   <li>the transition animation is removed, because a slide between peers implies one sits
 *       inside the other, and these do not.
 * </ul>
 */
public final class ConnexaBottomNavigation {

    private ConnexaBottomNavigation() {
    }

    public static void attach(Activity activity, BottomNavigationView navigationView) {
        BottomNavigationRouter router = new BottomNavigationRouter(activity.getClass());

        int selected = router.selectedMenuItemId();
        if (selected != 0) {
            // Set before the listener is attached so restoring selection is not mistaken
            // for the user tapping it.
            navigationView.setSelectedItemId(selected);
        }

        navigationView.setOnItemSelectedListener(item -> {
            Class<?> target = router.resolve(item.getItemId());
            if (target == null) {
                // Already here. Reporting the item as selected keeps it highlighted.
                return true;
            }
            activity.startActivity(new Intent(activity, target)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            removeTransition(activity);
            return true;
        });
    }

    /**
     * Suppresses the open animation.
     *
     * <p>A slide implies the new screen sits inside the one it came from. These are peers,
     * so they should replace each other without any sense of depth.
     */
    @SuppressWarnings("deprecation")
    private static void removeTransition(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            activity.overridePendingTransition(0, 0);
        }
    }
}
