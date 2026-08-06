package com.connexa.api.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventPublicationTest {

    private static final IdentityKey OWNER = new IdentityKey("https://identity.connexa", "alice");
    private static final Instant CREATED = Instant.parse("2026-06-01T09:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    @DisplayName("a published event carries the draft's details")
    void mapsDraftFieldsOntoTheEvent() {
        UUID eventId = UUID.randomUUID();

        EventSummary event = EventPublication.from(draft(), eventId, "Alice Khan", PUBLISHED_AT);

        assertThat(event.id()).isEqualTo(eventId);
        assertThat(event.title()).isEqualTo("Robotics Workshop");
        assertThat(event.startsAt()).isEqualTo(STARTS);
        assertThat(event.endsAt()).isEqualTo(STARTS.plusSeconds(7_200));
        assertThat(event.timeZone()).isEqualTo("Asia/Karachi");
        assertThat(event.category()).isEqualTo("Technology");
        assertThat(event.organizerName()).isEqualTo("Alice Khan");
        assertThat(event.createdAt()).isEqualTo(PUBLISHED_AT);
        assertThat(event.updatedAt()).isEqualTo(PUBLISHED_AT);
    }

    @Test
    @DisplayName("the draft's location becomes the public venue")
    void mapsLocationToVenue() {
        EventSummary event = EventPublication.from(draft(), UUID.randomUUID(), "Alice", PUBLISHED_AT);

        assertThat(event.venueName()).isEqualTo("Main Auditorium");
    }

    @Test
    @DisplayName("a published event starts at revision zero and is immediately discoverable")
    void publishesAtRevisionZero() {
        EventSummary event = EventPublication.from(draft(), UUID.randomUUID(), "Alice", PUBLISHED_AT);

        assertThat(event.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.revision()).isZero();
    }

    @Test
    @DisplayName("a short description becomes the summary unchanged")
    void keepsShortDescriptions() {
        assertThat(EventPublication.summaryOf("A hands-on robotics session."))
                .isEqualTo("A hands-on robotics session.");
    }

    @Test
    @DisplayName("whitespace across lines is collapsed into one")
    void collapsesWhitespace() {
        assertThat(EventPublication.summaryOf("  Line one.\n\n   Line two.\t Line three.  "))
                .isEqualTo("Line one. Line two. Line three.");
    }

    @Test
    @DisplayName("a long description is cut at a word boundary and marked as truncated")
    void truncatesLongDescriptionsAtAWordBoundary() {
        String description = "word ".repeat(300).strip();

        String summary = EventPublication.summaryOf(description);

        assertThat(summary).endsWith("…");
        assertThat(summary).doesNotContain("wor…");
        assertThat(summary.codePointCount(0, summary.length())).isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("a truncated summary still satisfies the event summary limit")
    void truncatedSummaryIsAcceptedByEventSummary() {
        OrganizerEventDraft longDraft = draftWithDescription("detail ".repeat(700).strip());

        EventSummary event = EventPublication.from(
                longDraft, UUID.randomUUID(), "Alice", PUBLISHED_AT);

        assertThat(event.summary().codePointCount(0, event.summary().length()))
                .isLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("a description with no spaces is still cut to the limit")
    void truncatesUnbrokenText() {
        String summary = EventPublication.summaryOf("x".repeat(900));

        assertThat(summary.codePointCount(0, summary.length())).isEqualTo(500);
        assertThat(summary).endsWith("…");
    }

    @Test
    @DisplayName("attribution prefers the display name")
    void prefersDisplayName() {
        VerifiedIdentity identity = new VerifiedIdentity(
                OWNER, "Alice Khan", "alice@example.com", Set.of(IdentityRole.ORGANIZER));

        assertThat(EventPublication.organizerNameOf(identity)).isEqualTo("Alice Khan");
    }

    @Test
    @DisplayName("attribution falls back to the email when no name is set")
    void fallsBackToEmail() {
        VerifiedIdentity identity = new VerifiedIdentity(
                OWNER, null, "alice@example.com", Set.of(IdentityRole.ORGANIZER));

        assertThat(EventPublication.organizerNameOf(identity)).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("attribution falls back to a generic label rather than blocking publication")
    void fallsBackToGenericLabel() {
        VerifiedIdentity identity = new VerifiedIdentity(
                OWNER, null, null, Set.of(IdentityRole.ORGANIZER));

        assertThat(EventPublication.organizerNameOf(identity))
                .isEqualTo(EventPublication.FALLBACK_ORGANIZER_NAME);
    }

    private static OrganizerEventDraft draft() {
        return draftWithDescription("A hands-on session covering the full build and test workflow.");
    }

    private static OrganizerEventDraft draftWithDescription(String description) {
        return new OrganizerEventDraft(
                UUID.randomUUID(),
                OWNER,
                "Robotics Workshop",
                description,
                "Main Auditorium",
                STARTS,
                STARTS.plusSeconds(7_200),
                "Asia/Karachi",
                "Technology",
                120,
                EventStatus.DRAFT,
                CREATED,
                CREATED);
    }
}
