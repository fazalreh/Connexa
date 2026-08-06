package com.connexa.api.infrastructure.search;

import com.connexa.api.domain.search.EmbeddingVector;
import java.util.Objects;
import java.util.UUID;

/**
 * @param contentHash what was embedded, so a re-index can skip unchanged text rather than
 *     paying for the same provider call again
 */
public record StoredEmbedding(UUID eventId, EmbeddingVector vector, String contentHash) {

    public StoredEmbedding {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(vector, "vector is required");
    }
}
