package com.connexa.api.infrastructure.search;

import com.connexa.api.domain.search.EmbeddingVector;

/**
 * Refuses to embed when no provider is configured.
 *
 * <p>Returning a placeholder vector instead would make search appear to work while ranking
 * results at random, which is far harder to notice than an explicit refusal.
 */
public final class UnavailableEmbeddingModel implements EmbeddingModel {

    private static final String NAME = "unconfigured";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EmbeddingVector embedDocument(String text) {
        throw new EmbeddingUnavailableException("No embedding provider is configured.");
    }

    @Override
    public EmbeddingVector embedQuery(String text) {
        throw new EmbeddingUnavailableException("No embedding provider is configured.");
    }
}
