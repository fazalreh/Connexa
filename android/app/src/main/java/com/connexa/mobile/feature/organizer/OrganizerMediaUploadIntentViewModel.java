package com.connexa.mobile.feature.organizer;

import com.connexa.mobile.core.organizer.MediaUploadIntent;
import com.connexa.mobile.core.organizer.OrganizerMediaType;
import java.util.Objects;
import java.util.UUID;

/** Display-safe representation of media metadata selected for an organizer event. */
public final class OrganizerMediaUploadIntentViewModel {

    private final UUID eventId;
    private final UUID requestId;
    private final OrganizerMediaType mediaType;
    private final String fileName;
    private final String mimeType;
    private final long contentLengthBytes;

    private OrganizerMediaUploadIntentViewModel(
            UUID eventId,
            UUID requestId,
            OrganizerMediaType mediaType,
            String fileName,
            String mimeType,
            long contentLengthBytes) {
        this.eventId = eventId;
        this.requestId = requestId;
        this.mediaType = mediaType;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.contentLengthBytes = contentLengthBytes;
    }

    public static OrganizerMediaUploadIntentViewModel from(MediaUploadIntent intent) {
        Objects.requireNonNull(intent, "intent is required");
        return new OrganizerMediaUploadIntentViewModel(
                intent.getEventId(),
                intent.getRequestId(),
                intent.getMediaType(),
                intent.getFileName(),
                intent.getMimeType(),
                intent.getContentLengthBytes());
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
}
