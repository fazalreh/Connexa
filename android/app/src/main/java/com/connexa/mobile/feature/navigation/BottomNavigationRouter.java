package com.connexa.mobile.feature.navigation;

import androidx.annotation.IdRes;

/**
 * Decides what a tap on the navigation bar should do.
 *
 * <p>Separated from the view so the rules can be tested without an Android runtime. They are
 * short, but they are the ones that make a bar feel right or wrong, and both are easy to get
 * subtly wrong in a listener.
 */
public final class BottomNavigationRouter {

    private final Class<?> currentScreen;

    public BottomNavigationRouter(Class<?> currentScreen) {
        this.currentScreen = currentScreen;
    }

    /**
     * @return the screen to open, or null to stay where we are
     */
    public Class<?> resolve(@IdRes int selectedMenuItemId) {
        BottomDestination selected = BottomDestination.forMenuItem(selectedMenuItemId);
        if (selected == null) {
            return null;
        }
        // Re-tapping the current tab must not relaunch the screen. Doing so would discard
        // scroll position and any text already typed, which reads as the app losing work.
        if (selected.getActivity().equals(currentScreen)) {
            return null;
        }
        return selected.getActivity();
    }

    /**
     * @return the entry to show as selected, or 0 for a screen that sits outside the bar and
     *     should therefore highlight nothing
     */
    @IdRes
    public int selectedMenuItemId() {
        BottomDestination destination = BottomDestination.forActivity(currentScreen);
        return destination == null ? 0 : destination.getMenuItemId();
    }
}
