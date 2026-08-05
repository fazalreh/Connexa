package com.connexa.api.domain.organizer;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventWindow;
import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * A private organizer-owned draft. It is intentionally not part of public event discovery.
 */
public record OrganizerEventDraft(
        UUID id,
        IdentityKey owner,
        String title,
        String description,
        String location,
        Instant startsAt,
        Instant endsAt,
        String timeZone,
        String category,
        int capacity,
        EventStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public OrganizerEventDraft {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(owner, "owner is required");
        CreateOrganizerEventCommand command = new CreateOrganizerEventCommand(
                title,
                description,
                location,
                startsAt,
                endsAt,
                timeZone,
                category,
                capacity);
        title = command.title();
        description = command.description();
        location = command.location();
        startsAt = command.startsAt();
        endsAt = command.endsAt();
        timeZone = command.timeZone();
        category = command.category();
        capacity = command.capacity();
        Objects.requireNonNull(status, "status is required");
        if (status != EventStatus.DRAFT) {
            throw new IllegalArgumentException("an organizer draft must have DRAFT status");
        }
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
        new EventWindow(startsAt, endsAt, ZoneId.of(timeZone));
    }
}
