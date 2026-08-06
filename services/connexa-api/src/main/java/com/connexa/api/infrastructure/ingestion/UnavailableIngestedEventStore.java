package com.connexa.api.infrastructure.ingestion;

import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.organizer.PublicationUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Refuses ingestion while storage is non-durable.
 *
 * <p>Deduplication depends on a unique constraint that only a real database provides.
 * Accepting announcements without it would republish the same event on every run.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class UnavailableIngestedEventStore implements IngestedEventStore {

    @Override
    public IngestionOutcome storeOnce(
            String sourceSystem,
            String sourceRecordId,
            String contentHash,
            EventSummary event,
            Integer totalCapacity) {
        throw new PublicationUnavailableException();
    }
}
