package com.connexa.api.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for organizer image uploads.
 *
 * <p>Uploads stay disabled until a cloud name, key and secret are all configured. The
 * secret signs upload requests and must never be sent to a client: an application
 * package is readable by anyone who installs it, so a secret shipped inside one is a
 * published secret.
 *
 * @param folder every asset is written beneath this prefix, which is what makes a
 *     returned delivery URL checkable rather than merely plausible
 */
@ConfigurationProperties(prefix = "connexa.media")
public record MediaProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String folder,
        long maxUploadBytes) {

    private static final String DEFAULT_FOLDER = "connexa/events";
    private static final long DEFAULT_MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;

    public MediaProperties {
        folder = folder == null || folder.isBlank() ? DEFAULT_FOLDER : folder.trim();
        // A phone camera writes 4-12 MB per photo. Larger than this is a video or a
        // mistake, and neither belongs on an event cover.
        maxUploadBytes = maxUploadBytes < 1 ? DEFAULT_MAX_UPLOAD_BYTES : maxUploadBytes;
    }

    public boolean isEnabled() {
        return isSet(cloudName) && isSet(apiKey) && isSet(apiSecret);
    }

    /** Where the client posts the file. Derived, so no route is configurable by mistake. */
    public String uploadUrl() {
        return "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
    }

    /** The host that serves what was uploaded, used to check a URL a client reports back. */
    public String deliveryPrefix() {
        return "https://res.cloudinary.com/" + cloudName.toLowerCase(Locale.ROOT) + "/";
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
