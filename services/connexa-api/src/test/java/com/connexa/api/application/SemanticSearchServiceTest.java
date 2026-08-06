package com.connexa.api.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import com.connexa.api.domain.event.EventCapacity;
import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.search.EmbeddingVector;
import com.connexa.api.infrastructure.attendance.AttendanceStore;
import com.connexa.api.infrastructure.event.EventCatalog;
import com.connexa.api.infrastructure.search.EmbeddingModel;
import com.connexa.api.infrastructure.search.EmbeddingStore;
import com.connexa.api.infrastructure.search.StoredEmbedding;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ranking is exercised with hand-built vectors rather than a live model: a test that calls
 * a provider is testing the provider, and would give a different answer each release.
 */
class SemanticSearchServiceTest {

    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");
    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");

    /** Three orthogonal directions standing in for unrelated topics. */
    private static final float[] ROBOTICS = {1f, 0f, 0f};
    private static final float[] CAREERS = {0f, 1f, 0f};
    private static final float[] POETRY = {0f, 0f, 1f};

    private final Map<UUID, EventSummary> catalogue = new HashMap<>();
    private final List<StoredEmbedding> index = new ArrayList<>();
    private final List<AttendanceState> attendance = new ArrayList<>();
    private float[] queryVector = ROBOTICS;

    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        service = new SemanticSearchService(
                new EmbeddingStore() {
                    @Override public void save(StoredEmbedding embedding, String model) { }
                    @Override public List<StoredEmbedding> findAll(String model) { return index; }
                    @Override public List<UUID> findUnindexed(String model, int limit) { return List.of(); }
                },
                new EmbeddingModel() {
                    @Override public String name() { return "test-model"; }
                    @Override public EmbeddingVector embedDocument(String text) {
                        return EmbeddingVector.of(queryVector);
                    }
                    @Override public EmbeddingVector embedQuery(String text) {
                        return EmbeddingVector.of(queryVector);
                    }
                },
                new EventCatalog() {
                    @Override public PageResponse<EventSummary> findPublished(EventQuery query) {
                        return PageResponse.empty(0, 20);
                    }
                    @Override public Optional<EventSummary> findById(UUID eventId) {
                        return Optional.ofNullable(catalogue.get(eventId));
                    }
                },
                new AttendanceStore() {
                    @Override public Optional<AttendanceState> find(IdentityKey i, UUID e) { return Optional.empty(); }
                    @Override public List<AttendanceState> findAll(IdentityKey i) { return attendance; }
                    @Override public AttendanceState setSaved(IdentityKey i, UUID e, boolean s, Instant t) { return null; }
                    @Override public AttendanceState setRsvp(IdentityKey i, UUID e, RsvpStatus r, Instant t) { return null; }
                    @Override public Optional<EventCapacity> findCapacity(UUID e) { return Optional.empty(); }
                });
    }

    private UUID indexed(String title, float[] vector) {
        UUID id = UUID.randomUUID();
        catalogue.put(id, new EventSummary(
                id, title, "Summary of " + title, STARTS, STARTS.plusSeconds(3600),
                "Asia/Karachi", "Main Auditorium", "General", "Connexa Events",
                EventStatus.PUBLISHED, 0L, STARTS, STARTS));
        index.add(new StoredEmbedding(id, EmbeddingVector.of(vector), "hash"));
        return id;
    }

    @Test
    @DisplayName("the closest event in meaning ranks first")
    void ranksTheClosestMatchFirst() {
        indexed("Poetry Night", POETRY);
        indexed("Robotics Workshop", ROBOTICS);
        indexed("Career Fair", CAREERS);
        queryVector = ROBOTICS;

        List<SemanticSearchService.ScoredEvent> results = service.search("anything", 10);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).event().title()).isEqualTo("Robotics Workshop");
    }

    @Test
    @DisplayName("unrelated events are excluded rather than ranked last")
    void dropsUnrelatedResults() {
        // An unrelated event under a specific query reads as a bug, not a weak match.
        indexed("Poetry Night", POETRY);
        indexed("Career Fair", CAREERS);
        queryVector = ROBOTICS;

        assertThat(service.search("robotics", 10)).isEmpty();
    }

    @Test
    @DisplayName("results are capped at the requested limit")
    void respectsTheLimit() {
        for (int i = 0; i < 5; i++) {
            indexed("Robotics " + i, ROBOTICS);
        }
        queryVector = ROBOTICS;

        assertThat(service.search("robotics", 2)).hasSize(2);
    }

    @Test
    @DisplayName("relevance is reported so a client can show why something matched")
    void reportsRelevance() {
        indexed("Robotics Workshop", ROBOTICS);
        queryVector = ROBOTICS;

        assertThat(service.search("robotics", 5).get(0).relevance()).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("a blank query returns nothing rather than everything")
    void blankQueryReturnsNothing() {
        indexed("Robotics Workshop", ROBOTICS);

        assertThat(service.search("   ", 10)).isEmpty();
        assertThat(service.search(null, 10)).isEmpty();
    }

    @Test
    @DisplayName("an empty index returns nothing without calling the model")
    void emptyIndexReturnsNothing() {
        assertThat(service.search("robotics", 10)).isEmpty();
    }

    @Test
    @DisplayName("an event missing from the public catalogue is not returned")
    void skipsEventsNotPubliclyVisible() {
        // The vector survives but the event was unpublished; discovery must not leak it.
        UUID hidden = indexed("Withdrawn Event", ROBOTICS);
        catalogue.remove(hidden);
        queryVector = ROBOTICS;

        assertThat(service.search("robotics", 10)).isEmpty();
    }

    @Test
    @DisplayName("recommendations follow what the attendee responded to")
    void recommendsFromTaste() {
        UUID liked = indexed("Robotics Workshop", ROBOTICS);
        indexed("Robotics Hackathon", ROBOTICS);
        indexed("Poetry Night", POETRY);
        attendance.add(new AttendanceState(liked, false, RsvpStatus.GOING, STARTS));

        List<SemanticSearchService.ScoredEvent> results = service.recommendationsFor(alice(), 10);

        assertThat(results).extracting(r -> r.event().title())
                .containsExactly("Robotics Hackathon");
    }

    @Test
    @DisplayName("events already responded to are never recommended")
    void excludesEventsAlreadyResponded() {
        UUID liked = indexed("Robotics Workshop", ROBOTICS);
        attendance.add(new AttendanceState(liked, false, RsvpStatus.GOING, STARTS));

        assertThat(service.recommendationsFor(alice(), 10))
                .noneMatch(result -> result.event().id().equals(liked));
    }

    @Test
    @DisplayName("an attendee with no history gets no recommendations")
    void noHistoryMeansNoRecommendations() {
        indexed("Robotics Workshop", ROBOTICS);

        assertThat(service.recommendationsFor(alice(), 10)).isEmpty();
    }

    @Test
    @DisplayName("a declined event does not shape recommendations")
    void declinedEventsAreNotTaste() {
        UUID declined = indexed("Robotics Workshop", ROBOTICS);
        indexed("Robotics Hackathon", ROBOTICS);
        attendance.add(new AttendanceState(declined, false, RsvpStatus.DECLINED, STARTS));

        assertThat(service.recommendationsFor(alice(), 10)).isEmpty();
    }

    @Test
    @DisplayName("a saved event counts as taste even without an RSVP")
    void savedEventsCountAsTaste() {
        UUID saved = indexed("Robotics Workshop", ROBOTICS);
        indexed("Robotics Hackathon", ROBOTICS);
        attendance.add(new AttendanceState(saved, true, null, STARTS));

        assertThat(service.recommendationsFor(alice(), 10))
                .extracting(r -> r.event().title()).containsExactly("Robotics Hackathon");
    }

    private static VerifiedIdentity alice() {
        return new VerifiedIdentity(ALICE, "Alice", null, Set.of(IdentityRole.ATTENDEE));
    }
}
