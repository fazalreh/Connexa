package com.connexa.mobile.feature.events;

import java.util.Objects;
import java.util.UUID;

/**
 * Gives every event a banner, whether or not anyone uploaded artwork.
 *
 * <p>Most events here arrive from an announcement email and will never have a cover. A grey
 * placeholder on nine cards out of ten makes a feed look broken, so each event gets a colour
 * of its own instead — derived from its id, so it is stable across sessions and devices and
 * the same event is always the same colour.
 *
 * <p>Only the hue varies. Saturation and lightness are fixed, which is the whole trick:
 * random colours look random, whereas one moving variable against two constants looks like a
 * palette somebody chose. Holding lightness down also guarantees the white category text on
 * top stays readable on every generated banner, without having to check each one.
 */
public final class EventBannerPalette {

    /** Deep enough that white text clears contrast on any hue. */
    private static final float LIGHTNESS_TOP = 0.34f;
    private static final float LIGHTNESS_BOTTOM = 0.22f;
    private static final float SATURATION = 0.42f;

    /**
     * Hues cluster in the blues and violets rather than spanning the wheel.
     *
     * <p>A full spectrum would put reds and yellows next to the brand blue and read as a
     * different application on every card. This span stays recognisably Connexa while still
     * telling two events apart.
     */
    private static final float HUE_START = 190f;
    private static final float HUE_SPAN = 130f;

    private EventBannerPalette() {
    }

    /** @return two ARGB colours, top then bottom, for a vertical gradient */
    public static int[] gradientFor(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId is required");
        float hue = hueFor(eventId);
        return new int[] {
                argb(hue, SATURATION, LIGHTNESS_TOP),
                argb(hue, SATURATION, LIGHTNESS_BOTTOM)
        };
    }

    static float hueFor(UUID eventId) {
        // Both halves are mixed in: taking only the low bits of a version 4 id would
        // discard most of the randomness that distinguishes two events.
        long mixed = eventId.getMostSignificantBits() ^ eventId.getLeastSignificantBits();
        long positive = mixed == Long.MIN_VALUE ? 0 : Math.abs(mixed);
        return HUE_START + (positive % 1000) / 1000f * HUE_SPAN;
    }

    private static int argb(float hue, float saturation, float lightness) {
        float chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
        float sector = (hue % 360f) / 60f;
        float second = chroma * (1 - Math.abs(sector % 2 - 1));
        float r;
        float g;
        float b;
        if (sector < 1) {
            r = chroma; g = second; b = 0;
        } else if (sector < 2) {
            r = second; g = chroma; b = 0;
        } else if (sector < 3) {
            r = 0; g = chroma; b = second;
        } else if (sector < 4) {
            r = 0; g = second; b = chroma;
        } else if (sector < 5) {
            r = second; g = 0; b = chroma;
        } else {
            r = chroma; g = 0; b = second;
        }
        float offset = lightness - chroma / 2;
        return 0xFF000000
                | (channel(r + offset) << 16)
                | (channel(g + offset) << 8)
                | channel(b + offset);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255)));
    }
}
