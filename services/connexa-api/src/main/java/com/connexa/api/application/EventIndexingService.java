package com.connexa.api.application;

import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.infrastructure.event.EventCatalog;
import com.connexa.api.infrastructure.search.EmbeddingModel;
import com.connexa.api.infrastructure.search.EmbeddingStore;
import com.connexa.api.infrastructure.search.EmbeddingUnavailableException;
import com.connexa.api.infrastructure.search.StoredEmbedding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Brings the semantic index up to date with the catalogue.
 *
 * <p>Runs in bounded batches and is safe to call repeatedly: only events with no vector for
 * the current model are embedded, so a repeat run costs nothing rather than re-paying for
 * every event in the catalogue.
 */
@Service
public class EventIndexingService {

    private static final Logger log = LoggerFactory.getLogger(EventIndexingService.class);

    private final EmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final EventCatalog eventCatalog;

    public EventIndexingService(
            EmbeddingStore embeddingStore,
            EmbeddingModel embeddingModel,
            EventCatalog eventCatalog) {
        this.embeddingStore = Objects.requireNonNull(embeddingStore, "embeddingStore");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.eventCatalog = Objects.requireNonNull(eventCatalog, "eventCatalog");
    }

    /**
     * Embeds up to {@code batchSize} events that have no vector yet.
     *
     * @return how many were indexed
     */
    public int indexPending(int batchSize) {
        List<UUID> pending = embeddingStore.findUnindexed(embeddingModel.name(), batchSize);
        int indexed = 0;
        for (UUID eventId : pending) {
            EventSummary event = eventCatalog.findById(eventId).orElse(null);
            if (event == null) {
                continue;
            }
            String text = SemanticSearchService.documentTextFor(event);
            try {
                embeddingStore.save(
                        new StoredEmbedding(
                                eventId, embeddingModel.embedDocument(text), sha256(text)),
                        embeddingModel.name());
                indexed++;
            } catch (EmbeddingUnavailableException exception) {
                // One provider failure must not abandon the whole batch; the remaining
                // events are still worth indexing and this one is retried next run.
                log.warn("Could not embed event {}", eventId, exception);
            }
        }
        return indexed;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
