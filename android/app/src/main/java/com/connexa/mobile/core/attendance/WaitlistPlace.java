package com.connexa.mobile.core.attendance;

/**
 * Where the caller stands in an event's queue.
 *
 * @param placeInQueue rank among those still waiting, or null once promoted
 * @param waiting false when the caller holds no place at all
 */
public record WaitlistPlace(Long placeInQueue, boolean waiting) {

    public static WaitlistPlace notWaiting() {
        return new WaitlistPlace(null, false);
    }
}
