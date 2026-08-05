package com.connexa.api.api.v1;

import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import java.time.Instant;
import java.util.UUID;

public record OrganizerEventDraftResponse(
        UUID id,
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

    public static OrganizerEventDraftResponse from(OrganizerEventDraft draft) {
        return new OrganizerEventDraftResponse(
                draft.id(),
                draft.title(),
                draft.description(),
                draft.location(),
                draft.startsAt(),
                draft.endsAt(),
                draft.timeZone(),
                draft.category(),
                draft.capacity(),
                draft.status(),
                draft.createdAt(),
                draft.updatedAt());
    }
}
