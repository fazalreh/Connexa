package com.connexa.api.infrastructure.search;

import java.util.List;
import java.util.UUID;

/** Persistence boundary for the semantic index. */
public interface EmbeddingStore {

    /** Replaces any vector already held for the event. */
    void save(StoredEmbedding embedding, String model);

    /**
     * Every vector produced by the given model.
     *
     * <p>Loaded in full because ranking is exact rather than approximate. That is the right
     * trade at this catalogue size; if it grows past tens of thousands, this is the method
     * to replace with an indexed nearest-neighbour query.
     */
    List<StoredEmbedding> findAll(String model);

    /** Event ids that have no current vector for this model and therefore need indexing. */
    List<UUID> findUnindexed(String model, int limit);
}
