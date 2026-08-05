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
        Instant updatedAt) {

    public EventSummary {
        Objects.requireNonNull(id, "id is required");
        requireText(title, "title");
        requireText(summary, "summary");
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
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
