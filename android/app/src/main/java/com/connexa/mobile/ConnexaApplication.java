package com.connexa.mobile;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

/**
 * Applies the device's dynamic colour scheme where the platform supports it.
 *
 * <p>On Android 12 and later this re-tints the app from the user's wallpaper. The generated
 * Connexa scheme remains the fallback on older devices and wherever the user has not enabled
 * dynamic colour, so the app is never unstyled — it simply prefers the personal scheme when
 * one exists.
 *
 * <p>Both schemes use the same Material role names, so nothing else in the app has to know
 * which one is active.
 */
public final class ConnexaApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
