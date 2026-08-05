package com.connexa.mobile.core.assistant;

import java.util.Objects;
import java.util.UUID;

/**
 * A safe, in-product event reference attached to an assistant response.
 *
 * <p>References deliberately identify an event by ID instead of carrying an arbitrary URL. The UI
 * can therefore route only to a known Connexa event screen.</p>
 */
public final class AssistantReference {

    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_DESCRIPTION_LENGTH = 280;

    private final UUID eventId;
    private final String title;
    private final String description;

    public AssistantReference(UUID eventId, String title, String description) {
        this.eventId = Objects.requireNonNull(eventId, "eventId is required");
        this.title = requireText(title, "title", MAX_TITLE_LENGTH);
        this.description = normalizeOptionalText(description, "description", MAX_DESCRIPTION_LENGTH);
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssistantReference)) {
            return false;
        }
        AssistantReference that = (AssistantReference) other;
        return eventId.equals(that.eventId)
                && title.equals(that.title)
                && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, title, description);
    }

    private static String requireText(String value, String field, int maximumLength) {
        String normalized = normalizeOptionalText(value, field, maximumLength);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value, String field, int maximumLength) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " exceeds the allowed length");
        }
        return normalized;
    }
}
