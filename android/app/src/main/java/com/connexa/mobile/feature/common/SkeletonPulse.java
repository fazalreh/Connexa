package com.connexa.mobile.feature.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * The slow breath that tells a placeholder apart from a broken layout.
 *
 * <p>Static grey blocks read as content that failed to load. Movement is what says the screen
 * is still working. It is deliberately unhurried — a fast pulse reads as an alarm, and the
 * point here is to reassure rather than to hurry anyone.
 *
 * <p>Fades rather than sweeps a highlight across. A shimmer needs a gradient the width of the
 * screen animating behind a mask, which is a lot of overdraw for a view that exists to be
 * replaced within a second or two.
 */
public final class SkeletonPulse {

    private static final long HALF_CYCLE_MILLIS = 900L;
    private static final float DIMMEST = 0.4f;

    private SkeletonPulse() {
    }

    /**
     * @return the running animator, which the caller must cancel when the placeholder goes
     *     away — an animator left running holds a reference to the view and keeps redrawing
     *     a screen nobody is looking at
     */
    public static ObjectAnimator start(View target) {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(target, View.ALPHA, 1f, DIMMEST);
        pulse.setDuration(HALF_CYCLE_MILLIS);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
        return pulse;
    }

    /** Cancels the pulse and restores full opacity, so a reused view is not left dimmed. */
    public static void stop(ObjectAnimator pulse, View target) {
        if (pulse != null) {
            pulse.cancel();
        }
        if (target != null) {
            target.setAlpha(1f);
        }
    }
}
