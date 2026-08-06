package com.connexa.api.domain.organizer;

import java.util.UUID;

/**
 * Raised when a draft does not exist, or exists but belongs to another organizer.
 *
 * <p>Both cases produce the same result deliberately: distinguishing them would let a
 * caller probe for the existence of drafts they do not own.
 */
public class OrganizerDraftNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID draftId;

    public OrganizerDraftNotFoundException(UUID draftId) {
        super("Organizer draft " + draftId + " not found");
        this.draftId = draftId;
    }

    public UUID draftId() {
        return draftId;
    }
}
