package com.connexa.mobile.core.organizer;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Local guardrails for an organizer event form.
 *
 * <p>The server remains the final authority for policy and availability. These checks make the
 * editor clear and prevent obviously incomplete requests from leaving the device.</p>
 */
public final class OrganizerEventDraftValidator {

    static final int MIN_TITLE_LENGTH = 3;
    static final int MAX_TITLE_LENGTH = 120;
    static final int MIN_DESCRIPTION_LENGTH = 20;
    static final int MAX_DESCRIPTION_LENGTH = 5_000;
    static final int MIN_LOCATION_LENGTH = 2;
    static final int MAX_LOCATION_LENGTH = 160;
    static final int MIN_CATEGORY_LENGTH = 2;
    static final int MAX_CATEGORY_LENGTH = 80;
    static final int MIN_CAPACITY = 1;
    static final int MAX_CAPACITY = 100_000;

    private OrganizerEventDraftValidator() {
    }

    public static OrganizerEventDraftValidationResult validate(OrganizerEventDraft draft) {
        Objects.requireNonNull(draft, "draft is required");
        EnumMap<OrganizerDraftField, String> errors = new EnumMap<>(OrganizerDraftField.class);

        validateText(
                normalized(draft.getTitle()),
                "Title",
                MIN_TITLE_LENGTH,
                MAX_TITLE_LENGTH,
                OrganizerDraftField.TITLE,
                errors);
        validateText(
                normalized(draft.getDescription()),
                "Description",
                MIN_DESCRIPTION_LENGTH,
                MAX_DESCRIPTION_LENGTH,
                OrganizerDraftField.DESCRIPTION,
                errors);
        validateText(
                normalized(draft.getLocation()),
                "Location",
                MIN_LOCATION_LENGTH,
                MAX_LOCATION_LENGTH,
                OrganizerDraftField.LOCATION,
                errors);
        validateText(
                normalized(draft.getCategory()),
                "Category",
                MIN_CATEGORY_LENGTH,
                MAX_CATEGORY_LENGTH,
                OrganizerDraftField.CATEGORY,
                errors);
        validateTimes(draft.getStartsAt(), draft.getEndsAt(), errors);
        validateCapacity(draft.getCapacity(), errors);

        return new OrganizerEventDraftValidationResult(errors);
    }

    private static void validateText(
            String value,
            String label,
            int minimumLength,
            int maximumLength,
            OrganizerDraftField field,
            EnumMap<OrganizerDraftField, String> errors) {
        if (value.isEmpty()) {
            errors.put(field, label + " is required.");
        } else if (value.length() < minimumLength) {
            errors.put(field, label + " must be at least " + minimumLength + " characters.");
        } else if (value.length() > maximumLength) {
            errors.put(field, label + " must be " + maximumLength + " characters or fewer.");
        }
    }

    private static void validateTimes(
            Instant startsAt,
            Instant endsAt,
            EnumMap<OrganizerDraftField, String> errors) {
        if (startsAt == null) {
            errors.put(OrganizerDraftField.STARTS_AT, "Start time is required.");
        }
        if (endsAt == null) {
            errors.put(OrganizerDraftField.ENDS_AT, "End time is required.");
        }
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            errors.put(OrganizerDraftField.ENDS_AT, "End time must be after the start time.");
        }
    }

    private static void validateCapacity(
            Integer capacity,
            EnumMap<OrganizerDraftField, String> errors) {
        if (capacity == null) {
            errors.put(OrganizerDraftField.CAPACITY, "Capacity is required.");
        } else if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            errors.put(
                    OrganizerDraftField.CAPACITY,
                    "Capacity must be between " + MIN_CAPACITY + " and " + MAX_CAPACITY + ".");
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
