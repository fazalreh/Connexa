package com.connexa.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.organizer.CreateOrganizerEventCommand;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import com.connexa.api.infrastructure.organizer.InMemoryOrganizerEventDraftStore;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrganizerEventDraftServiceTest {

    @Test
    void createsPrivateDraftsOnlyForOrganizerIdentities() {
        OrganizerEventDraftService service = new OrganizerEventDraftService(new InMemoryOrganizerEventDraftStore());
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
