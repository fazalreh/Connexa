package com.connexa.api.infrastructure.ingestion;

import com.connexa.api.domain.event.EventSummary;
import java.util.Objects;

/**
 * The result of submitting one source message.
 *
 * @param created false when the message had already produced this event
 */
public record IngestionOutcome(EventSummary event, boolean created) {

    public IngestionOutcome {
        Objects.requireNonNull(event, "event is required");
    }
}
