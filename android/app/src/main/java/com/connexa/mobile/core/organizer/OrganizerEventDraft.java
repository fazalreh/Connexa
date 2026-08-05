package com.connexa.mobile.core.organizer;

import java.time.Instant;

/**
 * Immutable, intentionally permissive snapshot of an event editor.
 *
 * <p>Fields may be absent while a person is working through the form. Use
 * {@link OrganizerEventDraftValidator} before allowing the draft to be submitted.</p>
 */
public final class OrganizerEventDraft {

    private final String title;
    private final String description;
    private final String location;
    private final Instant startsAt;
    private final Instant endsAt;
    private final String category;
    private final Integer capacity;

    public OrganizerEventDraft(
            String title,
            String description,
            String location,
            Instant startsAt,
            Instant endsAt,
            String category,
            Integer capacity) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.category = category;
        this.capacity = capacity;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getCategory() {
        return category;
    }

    public Integer getCapacity() {
        return capacity;
    }
}
