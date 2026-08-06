package com.connexa.api.infrastructure.ingestion;

import com.connexa.api.domain.event.EventSummary;

/**
 * Persistence boundary for events created from an approved announcement source.
 */
public interface IngestedEventStore {

    /**
     * Stores an event for a source message, or returns the one that message already
     * produced.
     *
     * <p>A scheduled reader re-reads its mailbox, so the same announcement is submitted
     * many times. Deduplication belongs here, at the point of the unique constraint,
     * rather than in the reader: two workers running at once would both believe they had
     * seen a message for the first time.
     */
    IngestionOutcome storeOnce(
            String sourceSystem,
            String sourceRecordId,
            String contentHash,
            EventSummary event,
            Integer totalCapacity);
}
