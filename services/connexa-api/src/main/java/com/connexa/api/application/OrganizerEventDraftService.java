package com.connexa.api.application;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.organizer.CreateOrganizerEventCommand;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import com.connexa.api.infrastructure.organizer.OrganizerEventDraftStore;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Creates and lists private event drafts for verified organizer identities.
 */
@Service
public class OrganizerEventDraftService {

    private final OrganizerEventDraftStore draftStore;

    public OrganizerEventDraftService(OrganizerEventDraftStore draftStore) {
        this.draftStore = Objects.requireNonNull(draftStore, "draftStore is required");
    }

    public OrganizerEventDraft create(VerifiedIdentity identity, CreateOrganizerEventCommand command) {
        requireOrganizer(identity);
        Objects.requireNonNull(command, "command is required");
        Instant now = Instant.now();
        OrganizerEventDraft draft = new OrganizerEventDraft(
                UUID.randomUUID(),
                identity.key(),
                command.title(),
                command.description(),
                command.location(),
                command.startsAt(),
                command.endsAt(),
                command.timeZone(),
                command.category(),
                command.capacity(),
                EventStatus.DRAFT,
                now,
                now);
        return draftStore.save(draft);
    }

    public PageResponse<OrganizerEventDraft> findAll(VerifiedIdentity identity, int page, int size) {
        requireOrganizer(identity);
        validatePage(page, size);
        List<OrganizerEventDraft> all = draftStore.findByOwner(identity.key());
        long total = all.size();
        long start = (long) page * size;
        if (start >= total) {
            return new PageResponse<>(List.of(), page, size, total);
        }
        int fromIndex = (int) start;
        int toIndex = Math.min(fromIndex + size, all.size());
        return new PageResponse<>(all.subList(fromIndex, toIndex), page, size, total);
    }

    private static void requireOrganizer(VerifiedIdentity identity) {
        Objects.requireNonNull(identity, "identity is required");
        if (!identity.hasRole(IdentityRole.ORGANIZER)) {
            throw new ActorNotAuthorizedException("manage organizer event drafts");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
