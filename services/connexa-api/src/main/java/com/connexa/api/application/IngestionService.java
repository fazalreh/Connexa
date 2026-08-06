package com.connexa.api.application;

import com.connexa.api.config.IngestionProperties;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.infrastructure.ingestion.IngestedEventStore;
import com.connexa.api.infrastructure.ingestion.IngestionOutcome;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Accepts events derived from approved announcement sources.
 */
@Service
public class IngestionService {

    private final IngestedEventStore ingestedEventStore;
    private final IngestionProperties properties;

    public IngestionService(
            IngestedEventStore ingestedEventStore, IngestionProperties properties) {
        this.ingestedEventStore = Objects.requireNonNull(ingestedEventStore, "ingestedEventStore");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Verifies the caller's shared token.
     *
     * <p>Compared in constant time. A byte-by-byte comparison that stops at the first
     * mismatch leaks the correct prefix through response timing, which is enough to
     * recover a token one character at a time.
     */
    public void requireIngestionAuthority(String presentedToken) {
        if (!properties.isEnabled()) {
            throw new ActorNotAuthorizedException("submit announcements");
        }
        byte[] expected = properties.apiToken().getBytes(StandardCharsets.UTF_8);
        byte[] presented = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, presented)) {
            throw new ActorNotAuthorizedException("submit announcements");
        }
    }

    /**
     * Stores one announcement-derived event, or returns the one that announcement
     * already produced.
     */
    public IngestionOutcome submit(
            String sourceSystem,
            String sourceRecordId,
            String contentHash,
            String title,
            String summary,
            Instant startsAt,
            Instant endsAt,
            String timeZone,
            String venueName,
            String category,
            String organizerName,
            Integer totalCapacity) {
        Instant now = Instant.now();
        EventSummary event = new EventSummary(
                UUID.randomUUID(),
                title,
                summary,
                startsAt,
                endsAt,
                timeZone,
                venueName,
                category,
                organizerName == null || organizerName.isBlank()
                        ? properties.organizerName()
                        : organizerName,
                EventStatus.PUBLISHED,
                0L,
                now,
                now);
        return ingestedEventStore.storeOnce(
                sourceSystem, sourceRecordId, contentHash, event, totalCapacity);
    }
}
