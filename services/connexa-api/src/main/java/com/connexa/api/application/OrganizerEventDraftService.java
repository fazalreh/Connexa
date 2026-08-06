package com.connexa.api.application;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.organizer.CreateOrganizerEventCommand;
import com.connexa.api.domain.organizer.EventPublication;
import com.connexa.api.domain.organizer.OrganizerDraftNotFoundException;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import com.connexa.api.infrastructure.organizer.OrganizerEventDraftStore;
import com.connexa.api.infrastructure.publication.EventPublicationStore;
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
    private final EventPublicationStore publicationStore;

    public OrganizerEventDraftService(
            OrganizerEventDraftStore draftStore,
            EventPublicationStore publicationStore) {
        this.draftStore = Objects.requireNonNull(draftStore, "draftStore is required");
        this.publicationStore = Objects.requireNonNull(publicationStore, "publicationStore is required");
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

    /**
     * Publishes one of the caller's drafts as a public event.
     *
     * <p>The draft is kept rather than consumed: it stays the organizer's private working
     * copy, and the link recorded against it is what stops a second publish producing a
     * duplicate listing.
     */
    public EventSummary publish(VerifiedIdentity identity, UUID draftId) {
        requireOrganizer(identity);
        Objects.requireNonNull(draftId, "draftId is required");

        OrganizerEventDraft draft = draftStore.findByIdAndOwner(draftId, identity.key())
                .orElseThrow(() -> new OrganizerDraftNotFoundException(draftId));

        EventSummary event = EventPublication.from(
                draft,
                UUID.randomUUID(),
                EventPublication.organizerNameOf(identity),
                Instant.now());
        return publicationStore.publish(draftId, event, draft.capacity());
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
