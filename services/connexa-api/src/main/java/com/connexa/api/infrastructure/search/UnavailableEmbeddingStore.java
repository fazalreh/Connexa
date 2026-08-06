package com.connexa.api.infrastructure.search;

import java.util.List;
import java.util.UUID;

/**
 * Empty semantic index for the non-durable mode.
 *
 * <p>Search degrades to keyword matching rather than failing: an attendee searching should
 * still get results when the deployment has no durable store.
 */
public final class UnavailableEmbeddingStore implements EmbeddingStore {

    @Override
    public void save(StoredEmbedding embedding, String model) {
        // Nothing durable to save into.
    }

    @Override
    public List<StoredEmbedding> findAll(String model) {
        return List.of();
    }

    @Override
    public List<UUID> findUnindexed(String model, int limit) {
        return List.of();
    }
}
