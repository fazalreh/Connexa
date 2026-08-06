package com.connexa.api.domain.media;

import java.util.Locale;
import java.util.Objects;

/**
 * Checks a delivery URL a client claims an upload produced.
 *
 * <p>The file goes straight from the phone to storage, so the resulting address comes back
 * through the client. Storing it unchecked would let anyone with an organizer account point
 * an event cover at any address they liked — a tracking pixel, an unrelated site's image, or
 * something worse shown under this application's name.
 *
 * <p>The check is deliberately strict. This service generated the storage name itself, so it
 * knows the exact shape of the only URL that upload can have produced. Anything else — a
 * different host, a different account, a path outside the folder, an added transformation —
 * is not that upload, whatever else it might be.
 */
public final class MediaDeliveryUrl {

    private static final String VERSION_PREFIX = "v";

    private MediaDeliveryUrl() {
    }

    /**
     * Returns the URL unchanged when it is genuinely the asset that was signed for.
     *
     * @param url what the client reported after uploading
     * @param deliveryPrefix {@code https://res.cloudinary.com/<account>/}
     * @param publicId the storage name this service chose and signed
     */
    public static String verify(String url, String deliveryPrefix, String publicId) {
        Objects.requireNonNull(deliveryPrefix, "deliveryPrefix is required");
        Objects.requireNonNull(publicId, "publicId is required");
        String candidate = url == null ? "" : url.trim();
        String expectedRoot = deliveryPrefix + "image/upload/";
        if (!candidate.toLowerCase(Locale.ROOT).startsWith(expectedRoot.toLowerCase(Locale.ROOT))) {
            throw new UnsupportedMediaException("That image address is not from this upload.");
        }

        String remainder = candidate.substring(expectedRoot.length());
        // Storage stamps a version segment onto the address it returns. It is the only
        // segment allowed between the upload root and the name that was signed; anything
        // else there would be a transformation the client chose rather than one we did.
        int firstSlash = remainder.indexOf('/');
        if (firstSlash > 0 && isVersionSegment(remainder.substring(0, firstSlash))) {
            remainder = remainder.substring(firstSlash + 1);
        }

        // The extension is assigned by storage from the file's actual content, so the
        // name is compared without it.
        String withoutExtension = stripExtension(remainder);
        if (!withoutExtension.equals(publicId)) {
            throw new UnsupportedMediaException("That image address is not from this upload.");
        }
        return candidate;
    }

    private static boolean isVersionSegment(String segment) {
        if (!segment.startsWith(VERSION_PREFIX) || segment.length() < 2) {
            return false;
        }
        for (int index = 1; index < segment.length(); index++) {
            if (!Character.isDigit(segment.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String stripExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        int lastSlash = path.lastIndexOf('/');
        return lastDot > lastSlash ? path.substring(0, lastDot) : path;
    }
}
