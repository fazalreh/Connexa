package com.connexa.api.application;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.search.EmbeddingVector;
import com.connexa.api.infrastructure.attendance.AttendanceStore;
import com.connexa.api.infrastructure.event.EventCatalog;
import com.connexa.api.infrastructure.search.EmbeddingModel;
import com.connexa.api.infrastructure.search.EmbeddingStore;
import com.connexa.api.infrastructure.search.StoredEmbedding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Meaning-based discovery over the event catalogue.
 *
 * <p>Ranking is exact rather than approximate. At this catalogue size loading every vector
 * and scoring it is both faster and simpler than maintaining an index, and it removes a
 * whole class of "why did this not appear" questions that approximate search creates.
 */
@Service
public class SemanticSearchService {

    /**
     * Results below this are related only in the loosest sense. Returning them makes the
     * feature look broken — an unrelated event under a specific query reads as a bug, not
     * as a weak match.
     */
    private static final double MINIMUM_RELEVANCE = 0.35;

    private final EmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final EventCatalog eventCatalog;
    private final AttendanceStore attendanceStore;

    public SemanticSearchService(
            EmbeddingStore embeddingStore,
            EmbeddingModel embeddingModel,
            EventCatalog eventCatalog,
            AttendanceStore attendanceStore) {
        this.embeddingStore = Objects.requireNonNull(embeddingStore, "embeddingStore");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.eventCatalog = Objects.requireNonNull(eventCatalog, "eventCatalog");
        this.attendanceStore = Objects.requireNonNull(attendanceStore, "attendanceStore");
    }

    /** Events whose meaning matches the phrase, most relevant first. */
    public List<ScoredEvent> search(String phrase, int limit) {
        if (phrase == null || phrase.isBlank()) {
            return List.of();
        }
        List<StoredEmbedding> indexed = embeddingStore.findAll(embeddingModel.name());
        if (indexed.isEmpty()) {
            return List.of();
        }
        EmbeddingVector query = embeddingModel.embedQuery(phrase.trim());
        return rank(indexed, query::similarityTo, Set.of(), limit);
    }

    /**
     * Suggestions built from what the caller has already responded to.
     *
     * <p>Their taste is represented as the mean of the events they said yes to. Averaging
     * unit vectors keeps the direction they have in common while cancelling out what makes
     * each one individual, which is exactly the signal wanted here.
     *
     * <p>Events they already responded to are excluded: recommending something already on
     * their calendar is the most obvious way for this to look unintelligent.
     */
    public List<ScoredEvent> recommendationsFor(VerifiedIdentity identity, int limit) {
        Objects.requireNonNull(identity, "identity is required");

        List<AttendanceState> attendance = attendanceStore.findAll(identity.key());
        Set<UUID> alreadySeen = attendance.stream()
                .map(AttendanceState::eventId)
                .collect(Collectors.toSet());
        Set<UUID> liked = attendance.stream()
                .filter(state -> state.rsvpStatus() == RsvpStatus.GOING
                        || state.rsvpStatus() == RsvpStatus.INTERESTED
                        || state.saved())
                .map(AttendanceState::eventId)
                .collect(Collectors.toSet());
        if (liked.isEmpty()) {
            return List.of();
        }

        List<StoredEmbedding> indexed = embeddingStore.findAll(embeddingModel.name());
        List<EmbeddingVector> tasteVectors = indexed.stream()
                .filter(stored -> liked.contains(stored.eventId()))
                .map(StoredEmbedding::vector)
                .toList();
        Optional<EmbeddingVector> taste = mean(tasteVectors);
        if (taste.isEmpty()) {
            return List.of();
        }
        return rank(indexed, taste.get()::similarityTo, alreadySeen, limit);
    }

    private List<ScoredEvent> rank(
            List<StoredEmbedding> indexed,
            Function<EmbeddingVector, Double> score,
            Set<UUID> excluded,
            int limit) {
        record Candidate(UUID eventId, double relevance) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (StoredEmbedding stored : indexed) {
            if (excluded.contains(stored.eventId())) {
                continue;
            }
            double relevance = score.apply(stored.vector());
            if (relevance >= MINIMUM_RELEVANCE) {
                candidates.add(new Candidate(stored.eventId(), relevance));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::relevance).reversed());

        // Details are fetched only for the results being returned, and through the public
        // catalogue so an unpublished event cannot leak into a search result.
        List<ScoredEvent> results = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (results.size() >= limit) {
                break;
            }
            eventCatalog.findById(candidate.eventId())
                    .ifPresent(event -> results.add(new ScoredEvent(event, candidate.relevance())));
        }
        return List.copyOf(results);
    }

    /** Component-wise mean, re-normalised so the result is comparable to stored vectors. */
    private static Optional<EmbeddingVector> mean(List<EmbeddingVector> vectors) {
        if (vectors.isEmpty()) {
            return Optional.empty();
        }
        int dimensions = vectors.get(0).dimensions();
        double[] totals = new double[dimensions];
        for (EmbeddingVector vector : vectors) {
            if (vector.dimensions() != dimensions) {
                // Mixed widths mean the index holds vectors from two models; averaging
                // them would produce a meaningless direction.
                return Optional.empty();
            }
            float[] components = vector.components();
            for (int index = 0; index < dimensions; index++) {
                totals[index] += components[index];
            }
        }
        try {
            return Optional.of(EmbeddingVector.of(totals));
        } catch (IllegalArgumentException opposedVectors) {
            // Tastes that cancel out exactly leave no direction to search along.
            return Optional.empty();
        }
    }

    /** @param relevance cosine similarity in [-1, 1]; higher is closer in meaning */
    public record ScoredEvent(EventSummary event, double relevance) {

        public ScoredEvent {
            Objects.requireNonNull(event, "event is required");
        }
    }

    /** Exposed for the indexer so it can describe what it embedded. */
    public static String documentTextFor(EventSummary event) {
        return String.join("\n",
                event.title(),
                event.category(),
                event.venueName(),
                event.summary());
    }
}
