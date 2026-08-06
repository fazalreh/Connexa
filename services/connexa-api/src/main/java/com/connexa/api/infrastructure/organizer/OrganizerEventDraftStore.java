package com.connexa.api.infrastructure.organizer;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for private organizer event drafts.
 */
public interface OrganizerEventDraftStore {

    OrganizerEventDraft save(OrganizerEventDraft draft);

    List<OrganizerEventDraft> findByOwner(IdentityKey owner);

    /**
     * One draft belonging to the given owner.
     *
     * <p>Ownership is part of the lookup rather than a check applied afterwards, so a draft
     * belonging to another organizer is indistinguishable from one that does not exist.
     */
    Optional<OrganizerEventDraft> findByIdAndOwner(UUID draftId, IdentityKey owner);
}
