package com.connexa.api.infrastructure.organizer;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Ephemeral local draft storage. A durable, access-controlled adapter can replace it later.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class InMemoryOrganizerEventDraftStore implements OrganizerEventDraftStore {

    private final ConcurrentMap<UUID, OrganizerEventDraft> drafts = new ConcurrentHashMap<>();

    @Override
    public OrganizerEventDraft save(OrganizerEventDraft draft) {
        Objects.requireNonNull(draft, "draft is required");
        drafts.put(draft.id(), draft);
        return draft;
    }

    @Override
    public List<OrganizerEventDraft> findByOwner(IdentityKey owner) {
        Objects.requireNonNull(owner, "owner is required");
        return drafts.values().stream()
                .filter(draft -> owner.equals(draft.owner()))
                .sorted(Comparator.comparing(OrganizerEventDraft::updatedAt).reversed())
                .toList();
    }
}
