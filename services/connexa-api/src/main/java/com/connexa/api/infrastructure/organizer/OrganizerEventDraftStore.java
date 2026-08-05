package com.connexa.api.infrastructure.organizer;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import java.util.List;

/**
 * Persistence boundary for private organizer event drafts.
 */
public interface OrganizerEventDraftStore {

    OrganizerEventDraft save(OrganizerEventDraft draft);

    List<OrganizerEventDraft> findByOwner(IdentityKey owner);
}
