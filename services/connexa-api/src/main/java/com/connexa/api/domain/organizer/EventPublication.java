package com.connexa.api.domain.organizer;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.VerifiedIdentity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Turns a private organizer draft into the public event it describes.
 *
 * <p>The two shapes do not line up field for field. A draft carries a long description for
 * the organizer's own use, while a public listing carries a short summary, so the summary
 * is derived here rather than being a second field the organizer has to fill in.
 */
public final class EventPublication {

    /** Matches the {@code summary} limit on {@link EventSummary}. */
    private static final int MAX_SUMMARY_LENGTH = 500;

    /** Attribution used when an identity provider supplied no name or email. */
    static final String FALLBACK_ORGANIZER_NAME = "Connexa Organizer";

    private EventPublication() {
    }

    public static EventSummary from(
            OrganizerEventDraft draft, UUID eventId, String organizerName, Instant publishedAt) {
        Objects.requireNonNull(draft, "draft is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(publishedAt, "publishedAt is required");
        return new EventSummary(
                eventId,
                draft.title(),
                summaryOf(draft.description()),
                draft.startsAt(),
                draft.endsAt(),
                draft.timeZone(),
                draft.location(),
                draft.category(),
                organizerName,
                EventStatus.PUBLISHED,
                0L,
                publishedAt,
                publishedAt);
    }

    /**
     * Public attribution for an organizer.
     *
     * <p>Falls back through display name and email to a generic label. Publishing must not
     * depend on how completely an identity provider populates a profile — an event with
     * plain attribution is better than one that cannot be published at all.
     */
    public static String organizerNameOf(VerifiedIdentity identity) {
        Objects.requireNonNull(identity, "identity is required");
        if (identity.displayName() != null) {
            return identity.displayName();
        }
        if (identity.email() != null) {
            return identity.email();
        }
        return FALLBACK_ORGANIZER_NAME;
    }

    /**
     * Condenses a draft description into a listing summary.
     *
     * <p>Whitespace is collapsed so a multi-paragraph description reads as one line. Longer
     * text is cut at a word boundary and marked with an ellipsis, so a truncated summary is
     * visibly truncated rather than looking like a sentence that simply stops.
     */
    public static String summaryOf(String description) {
        Objects.requireNonNull(description, "description is required");
        String collapsed = description.strip().replaceAll("\\s+", " ");
        if (collapsed.codePointCount(0, collapsed.length()) <= MAX_SUMMARY_LENGTH) {
            return collapsed;
        }

        // Reserve one code point for the ellipsis.
        int cutIndex = collapsed.offsetByCodePoints(0, MAX_SUMMARY_LENGTH - 1);
        String head = collapsed.substring(0, cutIndex);
        int lastSpace = head.lastIndexOf(' ');
        if (lastSpace > 0) {
            head = head.substring(0, lastSpace);
        }
        return head.stripTrailing() + "…";
    }
}
