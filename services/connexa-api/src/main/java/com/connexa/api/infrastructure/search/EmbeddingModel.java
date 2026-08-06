package com.connexa.api.infrastructure.search;

import com.connexa.api.domain.search.EmbeddingVector;

/**
 * Turns text into a vector.
 *
 * <p>Behind an interface so ranking can be tested with deterministic vectors: a test that
 * calls a live model is testing the model, not the search.
 */
public interface EmbeddingModel {

    /** Identifies which model produced a vector, so stored vectors can be invalidated. */
    String name();

    /**
     * Embeds text for storage against an event.
     *
     * <p>Separate from {@link #embedQuery} because providers ask which side of the
     * comparison the text is on, and using the wrong one degrades results measurably.
     */
    EmbeddingVector embedDocument(String text);

    /** Embeds a user's search phrase. */
    EmbeddingVector embedQuery(String text);
}
