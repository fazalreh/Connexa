package com.connexa.api.api.v1;

import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.infrastructure.ingestion.IngestionOutcome;
import java.util.UUID;

/**
 * @param created false when the submission matched an announcement already ingested
 */
public record IngestionEventResponse(UUID eventId, boolean created, String title) {

    public static IngestionEventResponse from(IngestionOutcome outcome) {
        EventSummary event = outcome.event();
        return new IngestionEventResponse(event.id(), outcome.created(), event.title());
    }
}
