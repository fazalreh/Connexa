package com.connexa.mobile.core.organizer;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provider-neutral metadata describing a requested media upload.
 *
 * <p>This type deliberately carries no local file path, upload endpoint, secret, or credential.
 * The eventual upload implementation receives the content separately after the service accepts
 * this intent.</p>
 */
public final class MediaUploadIntent {

    public static final long MAX_CONTENT_LENGTH_BYTES = 25L * 1024L * 1024L;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final Pattern MIME_TYPE_PATTERN = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9!#$&^_.+\\-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+\\-]*$");

    private final UUID eventId;
    private final UUID requestId;
    private final OrganizerMediaType mediaType;
    private final String fileName;
    private final String mimeType;
    private final long contentLengthBytes;

    public MediaUploadIntent(
            UUID eventId,
            UUID requestId,
            OrganizerMediaType mediaType,
            String fileName,
            String mimeType,
            long contentLengthBytes) {
        this.eventId = Objects.requireNonNull(eventId, "eventId is required");
        this.requestId = Objects.requireNonNull(requestId, "requestId is required");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType is required");
        this.fileName = validateFileName(fileName);
        this.mimeType = validateMimeType(mimeType, mediaType);
        if (contentLengthBytes < 1 || contentLengthBytes > MAX_CONTENT_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "contentLengthBytes must be between one and " + MAX_CONTENT_LENGTH_BYTES);
        }
        this.contentLengthBytes = contentLengthBytes;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public OrganizerMediaType getMediaType() {
        return mediaType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getContentLengthBytes() {
        return contentLengthBytes;
    }

    private static String validateFileName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("fileName is required");
        }
        if (normalized.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("fileName is too long");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '/' || character == '\\' || Character.isISOControl(character)) {
                throw new IllegalArgumentException("fileName must not contain a path or control character");
            }
        }
        return normalized;
    }

    private static String validateMimeType(String value, OrganizerMediaType mediaType) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!MIME_TYPE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("mimeType must be a valid media type");
        }
        if ((mediaType == OrganizerMediaType.COVER_IMAGE
                || mediaType == OrganizerMediaType.GALLERY_IMAGE)
                && !normalized.startsWith("image/")) {
            throw new IllegalArgumentException("image media must use an image mimeType");
        }
        return normalized;
    }
}
