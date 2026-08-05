package com.connexa.api.domain.organizer;

import com.connexa.api.domain.event.EventWindow;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Validated organizer input for a private event draft.
 */
public record CreateOrganizerEventCommand(
        String title,
        String description,
        String location,
        Instant startsAt,
        Instant endsAt,
        String timeZone,
        String category,
        int capacity) {

    private static final int MIN_TITLE_LENGTH = 3;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MIN_DESCRIPTION_LENGTH = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 5_000;
    private static final int MIN_LOCATION_LENGTH = 2;
    private static final int MAX_LOCATION_LENGTH = 160;
    private static final int MAX_TIME_ZONE_LENGTH = 64;
    private static final int MIN_CATEGORY_LENGTH = 2;
    private static final int MAX_CATEGORY_LENGTH = 80;
    private static final int MAX_CAPACITY = 100_000;

    public CreateOrganizerEventCommand {
        title = requireText(title, "title", MIN_TITLE_LENGTH, MAX_TITLE_LENGTH);
        description = requireText(
                description, "description", MIN_DESCRIPTION_LENGTH, MAX_DESCRIPTION_LENGTH);
        location = requireText(location, "location", MIN_LOCATION_LENGTH, MAX_LOCATION_LENGTH);
        timeZone = requireText(timeZone, "timeZone", 1, MAX_TIME_ZONE_LENGTH);
        category = requireText(category, "category", MIN_CATEGORY_LENGTH, MAX_CATEGORY_LENGTH);
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be between 1 and " + MAX_CAPACITY);
        }
        new EventWindow(
                Objects.requireNonNull(startsAt, "startsAt is required"),
                Objects.requireNonNull(endsAt, "endsAt is required"),
                ZoneId.of(timeZone));
    }

    private static String requireText(String value, String field, int minLength, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() < minLength) {
            throw new IllegalArgumentException(field + " must be at least " + minLength + " characters");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
