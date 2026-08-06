package com.connexa.api.domain.event;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

public record EventSummary(
        UUID id,
        String title,
        String summary,
        Instant startsAt,
        Instant endsAt,
        String timeZone,
        String venueName,
        String category,
        String organizerName,
        EventStatus status,
        long revision,
        Instant createdAt,
        Instant updatedAt,
        String coverImageUrl) {

    /**
     * Most events have no cover.
     *
     * <p>An announcement arriving by email carries no artwork, so the common case is stated
     * without having to write null at every construction site. The feed draws a generated
     * banner when this is absent rather than leaving a gap.
     */
    public EventSummary(
            UUID id,
            String title,
            String summary,
            Instant startsAt,
            Instant endsAt,
            String timeZone,
            String venueName,
            String category,
            String organizerName,
            EventStatus status,
            long revision,
            Instant createdAt,
            Instant updatedAt) {
        this(id, title, summary, startsAt, endsAt, timeZone, venueName, category,
                organizerName, status, revision, createdAt, updatedAt, null);
    }

    public EventSummary {
        Objects.requireNonNull(id, "id is required");
        requireText(title, "title", 160);
        requireText(summary, "summary", 500);
        requireText(timeZone, "timeZone");
        requireText(venueName, "venueName");
        requireText(category, "category");
        requireText(organizerName, "organizerName");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        new EventWindow(startsAt, endsAt, ZoneId.of(timeZone));
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be zero or greater");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        // Blank and absent mean the same thing here, and collapsing them means every
        // reader has one case to handle rather than two.
        coverImageUrl = coverImageUrl == null || coverImageUrl.isBlank()
                ? null
                : coverImageUrl.trim();
        if (coverImageUrl != null && coverImageUrl.length() > 512) {
            throw new IllegalArgumentException("coverImageUrl must not exceed 512 characters");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void requireText(String value, String field, int maximumLength) {
        requireText(value, field);
        if (value.codePointCount(0, value.length()) > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters");
        }
    }
}
