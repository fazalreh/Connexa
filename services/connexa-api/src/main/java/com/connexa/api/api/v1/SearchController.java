package com.connexa.api.api.v1;

import com.connexa.api.application.EventIndexingService;
import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.application.SemanticSearchService;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meaning-based discovery.
 *
 * <p>Search is public, matching the rest of event discovery. Recommendations are not: they
 * are derived from one person's history and would disclose it.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final SemanticSearchService semanticSearchService;
    private final EventIndexingService eventIndexingService;
    private final IdentityAccessService identityAccessService;

    public SearchController(
            SemanticSearchService semanticSearchService,
            EventIndexingService eventIndexingService,
            IdentityAccessService identityAccessService) {
        this.semanticSearchService = semanticSearchService;
        this.eventIndexingService = eventIndexingService;
        this.identityAccessService = identityAccessService;
    }

    @GetMapping("/events/search")
    public List<SearchResultResponse> search(
            @RequestParam(name = "q") @Size(min = 2, max = 200) String phrase,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return semanticSearchService.search(phrase, limit).stream()
                .map(SearchResultResponse::from)
                .toList();
    }

    @GetMapping("/me/recommendations")
    public List<SearchResultResponse> recommendations(
            HttpServletRequest request,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        VerifiedIdentity identity = identityAccessService.requireVerifiedIdentity(request);
        return semanticSearchService.recommendationsFor(identity, limit).stream()
                .map(SearchResultResponse::from)
                .toList();
    }

    /**
     * Brings the index up to date. Restricted to organizers because it spends provider
     * quota, so it must not be reachable by anyone who finds the URL.
     */
    @PostMapping("/search/index")
    public IndexRunResponse index(
            HttpServletRequest request,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int batchSize) {
        VerifiedIdentity identity = identityAccessService.requireVerifiedIdentity(request);
        if (!identity.hasRole(IdentityRole.ORGANIZER)) {
            throw new ActorNotAuthorizedException("rebuild the search index");
        }
        return new IndexRunResponse(eventIndexingService.indexPending(batchSize));
    }

    public record IndexRunResponse(int indexed) {
    }
}
