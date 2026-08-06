package com.connexa.api.domain.media;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A validated description of an image an organizer is about to upload.
 *
 * <p>Carries no file path, endpoint or credential. The content is transferred separately
 * once the service has signed the request.
 */
public record MediaUpload(String mimeType, long contentLengthBytes) {

    /**
     * Raster formats only.
     *
     * <p>SVG is excluded deliberately. It is a document that can carry script, and anything
     * served from the delivery host runs in that host's origin, so accepting one would turn
     * an image upload into a way to publish executable content.
     */
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    public MediaUpload {
        mimeType = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new UnsupportedMediaException(
                    "Cover images must be JPEG, PNG, WebP or HEIC.");
        }
        if (contentLengthBytes < 1) {
            throw new UnsupportedMediaException("The selected file is empty.");
        }
    }

    /**
     * Picks where the asset will be stored.
     *
     * <p>The name is generated here rather than taken from the client so an upload cannot
     * be aimed at an existing asset. A random name also means one organizer cannot guess
     * or overwrite another's cover.
     */
    public String publicIdUnder(String folder) {
        return trimSlashes(folder) + "/" + UUID.randomUUID();
    }

    public void requireWithin(long maxUploadBytes) {
        if (contentLengthBytes > maxUploadBytes) {
            throw new UnsupportedMediaException(
                    "Images must be smaller than " + (maxUploadBytes / (1024 * 1024)) + " MB.");
        }
    }

    private static String trimSlashes(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("folder is required");
        }
        return normalized;
    }
}
