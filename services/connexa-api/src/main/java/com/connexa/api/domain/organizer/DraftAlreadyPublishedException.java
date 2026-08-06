package com.connexa.api.domain.organizer;

import java.util.UUID;

/**
 * Raised when a draft that already produced a public event is published again.
 *
 * <p>Reported rather than silently ignored: an organizer who edited a draft and published
 * it a second time expects those edits to be live, and quietly returning the original
 * event would hide that they are not.
 */
public class DraftAlreadyPublishedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID draftId;
    private final UUID publishedEventId;

    public DraftAlreadyPublishedException(UUID draftId, UUID publishedEventId) {
        super("Organizer draft " + draftId + " is already published as event " + publishedEventId);
        this.draftId = draftId;
        this.publishedEventId = publishedEventId;
    }

    public UUID draftId() {
        return draftId;
    }

    public UUID publishedEventId() {
        return publishedEventId;
    }
}
