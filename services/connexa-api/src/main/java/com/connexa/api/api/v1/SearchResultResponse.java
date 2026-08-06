package com.connexa.api.api.v1;

import com.connexa.api.application.SemanticSearchService;
import com.connexa.api.domain.event.EventSummary;

/**
 * @param relevance cosine similarity, surfaced so a client can show why something matched
 *     rather than presenting an unexplained ordering
 */
public record SearchResultResponse(EventSummary event, double relevance) {

    public static SearchResultResponse from(SemanticSearchService.ScoredEvent scored) {
        return new SearchResultResponse(scored.event(), scored.relevance());
    }
}
