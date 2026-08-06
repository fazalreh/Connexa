package com.connexa.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.organizer.CreateOrganizerEventCommand;
import com.connexa.api.domain.organizer.OrganizerDraftNotFoundException;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import com.connexa.api.domain.organizer.PublicationUnavailableException;
import com.connexa.api.infrastructure.organizer.InMemoryOrganizerEventDraftStore;
import com.connexa.api.infrastructure.publication.UnavailableEventPublicationStore;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizerEventDraftServiceTest {

    @Test
    void createsPrivateDraftsOnlyForOrganizerIdentities() {
        OrganizerEventDraftService service = serviceWithInMemoryStores();
        CreateOrganizerEventCommand command = validCommand();
        VerifiedIdentity organizer = identityWith(IdentityRole.ORGANIZER);

        OrganizerEventDraft draft = service.create(organizer, command);

        assertEquals(EventStatus.DRAFT, draft.status());
        assertEquals("Community hall", draft.location());
        assertEquals(80, draft.capacity());
        assertEquals(1L, service.findAll(organizer, 0, 20).total());
        assertThrows(ActorNotAuthorizedException.class, () -> service.create(
                identityWith(IdentityRole.ATTENDEE), command));
    }

    @Test
    void publishingRequiresAnOrganizerIdentity() {
        OrganizerEventDraftService service = serviceWithInMemoryStores();
        VerifiedIdentity organizer = identityWith(IdentityRole.ORGANIZER);
        OrganizerEventDraft draft = service.create(organizer, validCommand());

        assertThrows(ActorNotAuthorizedException.class, () -> service.publish(
                identityWith(IdentityRole.ATTENDEE), draft.id()));
    }

    @Test
    void publishingAnUnknownDraftIsRejectedBeforeReachingTheStore() {
        OrganizerEventDraftService service = serviceWithInMemoryStores();

        assertThrows(OrganizerDraftNotFoundException.class, () -> service.publish(
                identityWith(IdentityRole.ORGANIZER), UUID.randomUUID()));
    }

    @Test
    void oneOrganizerCannotPublishAnothersDraft() {
        OrganizerEventDraftService service = serviceWithInMemoryStores();
        OrganizerEventDraft draft = service.create(identityWith(IdentityRole.ORGANIZER), validCommand());

        VerifiedIdentity otherOrganizer = new VerifiedIdentity(
                new IdentityKey("test-issuer", "other-organizer"),
                "Other Organizer",
                null,
                Set.of(IdentityRole.ORGANIZER));

        assertThrows(OrganizerDraftNotFoundException.class,
                () -> service.publish(otherOrganizer, draft.id()));
    }

    @Test
    void publishingIsRefusedWhileStorageIsNotDurable() {
        OrganizerEventDraftService service = serviceWithInMemoryStores();
        VerifiedIdentity organizer = identityWith(IdentityRole.ORGANIZER);
        OrganizerEventDraft draft = service.create(organizer, validCommand());

        assertThrows(PublicationUnavailableException.class,
                () -> service.publish(organizer, draft.id()));
    }

    private static OrganizerEventDraftService serviceWithInMemoryStores() {
        return new OrganizerEventDraftService(
                new InMemoryOrganizerEventDraftStore(),
                new UnavailableEventPublicationStore());
    }

    @Test
    void rejectsInvalidCapacityBeforeCreatingADraft() {
        assertThrows(IllegalArgumentException.class, () -> new CreateOrganizerEventCommand(
                "Event",
                "Description",
                "Community hall",
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-05T11:00:00Z"),
                "Asia/Karachi",
                "Community",
                0));
    }

    private static CreateOrganizerEventCommand validCommand() {
        return new CreateOrganizerEventCommand(
                "Community meetup",
                "A planned community event.",
                "Community hall",
                Instant.parse("2026-08-05T10:00:00Z"),
                Instant.parse("2026-08-05T11:00:00Z"),
                "Asia/Karachi",
                "Community",
                80);
    }

    private static VerifiedIdentity identityWith(IdentityRole role) {
        return new VerifiedIdentity(
                new IdentityKey("test-issuer", role.name().toLowerCase()),
                role.name(),
                null,
                Set.of(role));
    }
}
