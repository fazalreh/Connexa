package com.connexa.api.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.domain.event.EventQuery;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.infrastructure.persistence.JdbcAdapterTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcEventCatalogTest {

    private static final Instant JUNE = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant JULY = Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant AUGUST = Instant.parse("2026-08-01T10:00:00Z");

    private NamedParameterJdbcTemplate jdbc;
    private JdbcEventCatalog catalog;

    @BeforeEach
    void setUp() {
        jdbc = JdbcAdapterTestSupport.freshDatabase();
        catalog = new JdbcEventCatalog(jdbc);
    }

    @Test
    @DisplayName("discovery returns published events ordered by start time")
    void returnsPublishedEventsInStartOrder() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "August Summit", "Closing summit", AUGUST, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "June Kickoff", "Opening session", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "July Workshop", "Hands on lab", JULY, EventStatus.PUBLISHED);

        PageResponse<EventSummary> page = catalog.findPublished(new EventQuery(null, null, null, 0, 20));

        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.items()).extracting(EventSummary::title)
                .containsExactly("June Kickoff", "July Workshop", "August Summit");
    }

    @Test
    @DisplayName("discovery hides every status other than PUBLISHED")
    void hidesUnpublishedEvents() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "Visible", "Published event", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Hidden Draft", "Draft event", JUNE, EventStatus.DRAFT);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Hidden Review", "Pending event", JUNE, EventStatus.PENDING_REVIEW);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Hidden Cancelled", "Cancelled event", JUNE, EventStatus.CANCELLED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Hidden Archived", "Archived event", JUNE, EventStatus.ARCHIVED);

        PageResponse<EventSummary> page = catalog.findPublished(new EventQuery(null, null, null, 0, 20));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).extracting(EventSummary::title).containsExactly("Visible");
    }

    @Test
    @DisplayName("search matches title or summary regardless of case")
    void searchMatchesTitleOrSummaryCaseInsensitively() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "Robotics Demo", "Arms and sensors", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Career Fair", "Meet ROBOTICS teams", JULY, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Poetry Night", "Open mic", AUGUST, EventStatus.PUBLISHED);

        PageResponse<EventSummary> page =
                catalog.findPublished(new EventQuery("robotics", null, null, 0, 20));

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.items()).extracting(EventSummary::title)
                .containsExactly("Robotics Demo", "Career Fair");
    }

    @Test
    @DisplayName("wildcard characters in a search term are matched literally")
    void treatsWildcardCharactersAsLiterals() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "100% Renewable", "Energy panel", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Plain Meetup", "Nothing special", JULY, EventStatus.PUBLISHED);

        PageResponse<EventSummary> page =
                catalog.findPublished(new EventQuery("100%", null, null, 0, 20));

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).extracting(EventSummary::title).containsExactly("100% Renewable");
    }

    @Test
    @DisplayName("the LIKE escape character itself is searchable")
    void treatsEscapeCharacterAsLiteral() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "Ship It!", "Release party", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "Ship It", "Decoy without punctuation", JULY,
                EventStatus.PUBLISHED);

        PageResponse<EventSummary> page =
                catalog.findPublished(new EventQuery("ship it!", null, null, 0, 20));

        assertThat(page.items()).extracting(EventSummary::title).containsExactly("Ship It!");
    }

    @Test
    @DisplayName("an underscore in a search term does not match an arbitrary character")
    void treatsUnderscoreAsLiteral() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "Team_Sync", "Weekly sync", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "TeamXSync", "Decoy", JULY, EventStatus.PUBLISHED);

        PageResponse<EventSummary> page =
                catalog.findPublished(new EventQuery("team_sync", null, null, 0, 20));

        assertThat(page.items()).extracting(EventSummary::title).containsExactly("Team_Sync");
    }

    @Test
    @DisplayName("the from and to bounds filter on start time inclusively")
    void filtersByDateWindow() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "June Kickoff", "Opening", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "July Workshop", "Lab", JULY, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "August Summit", "Closing", AUGUST, EventStatus.PUBLISHED);

        PageResponse<EventSummary> page =
                catalog.findPublished(new EventQuery(null, JULY, AUGUST, 0, 20));

        assertThat(page.items()).extracting(EventSummary::title)
                .containsExactly("July Workshop", "August Summit");
    }

    @Test
    @DisplayName("paging reports the full total on every page")
    void pagesResultsWhileReportingFullTotal() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "June Kickoff", "Opening", JUNE, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "July Workshop", "Lab", JULY, EventStatus.PUBLISHED);
        JdbcAdapterTestSupport.insertEvent(jdbc, "August Summit", "Closing", AUGUST, EventStatus.PUBLISHED);

        PageResponse<EventSummary> first = catalog.findPublished(new EventQuery(null, null, null, 0, 2));
        PageResponse<EventSummary> second = catalog.findPublished(new EventQuery(null, null, null, 1, 2));

        assertThat(first.total()).isEqualTo(3L);
        assertThat(first.items()).extracting(EventSummary::title)
                .containsExactly("June Kickoff", "July Workshop");
        assertThat(second.total()).isEqualTo(3L);
        assertThat(second.items()).extracting(EventSummary::title).containsExactly("August Summit");
    }

    @Test
    @DisplayName("a page past the end is empty but still reports the total")
    void returnsEmptyPageBeyondEnd() {
        JdbcAdapterTestSupport.insertEvent(jdbc, "June Kickoff", "Opening", JUNE, EventStatus.PUBLISHED);

        PageResponse<EventSummary> page = catalog.findPublished(new EventQuery(null, null, null, 5, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.page()).isEqualTo(5);
    }

    @Test
    @DisplayName("an empty catalog reports no results")
    void returnsEmptyPageWhenCatalogIsEmpty() {
        PageResponse<EventSummary> page = catalog.findPublished(new EventQuery(null, null, null, 0, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    @DisplayName("lookup by id round-trips every stored field")
    void findsPublishedEventByIdWithAllFields() {
        UUID id = JdbcAdapterTestSupport.insertEvent(
                jdbc, "June Kickoff", "Opening session", JUNE, EventStatus.PUBLISHED);

        EventSummary summary = catalog.findById(id).orElseThrow();

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.title()).isEqualTo("June Kickoff");
        assertThat(summary.summary()).isEqualTo("Opening session");
        assertThat(summary.startsAt()).isEqualTo(JUNE);
        assertThat(summary.endsAt()).isEqualTo(JUNE.plusSeconds(3_600));
        assertThat(summary.timeZone()).isEqualTo("Asia/Karachi");
        assertThat(summary.venueName()).isEqualTo("Main Auditorium");
        assertThat(summary.category()).isEqualTo("Technology");
        assertThat(summary.organizerName()).isEqualTo("Connexa Events");
        assertThat(summary.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(summary.revision()).isZero();
    }

    @Test
    @DisplayName("lookup by id refuses to expose an unpublished event")
    void doesNotFindUnpublishedEventById() {
        UUID draftId = JdbcAdapterTestSupport.insertEvent(
                jdbc, "Hidden Draft", "Draft event", JUNE, EventStatus.DRAFT);

        assertThat(catalog.findById(draftId)).isEmpty();
    }

    @Test
    @DisplayName("lookup by an unknown id is empty")
    void returnsEmptyForUnknownId() {
        assertThat(catalog.findById(UUID.randomUUID())).isEmpty();
    }
}
