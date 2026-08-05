package com.connexa.mobile.feature.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventPage;
import com.connexa.mobile.core.events.EventSummary;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.Test;

public final class EventListPresenterTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    public void showsAnEmptyStateForAnEmptyPage() {
        RecordingView view = new RecordingView();
        EventListPresenter presenter = new EventListPresenter(
                new StaticEventDataSource(new EventPage(List.of(), 0, 20, 0)),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                view);

        presenter.load("");

        assertEquals(List.of("loading", "empty"), view.states);
    }

    @Test
    public void showsEventsReturnedByTheDataSource() {
        RecordingView view = new RecordingView();
        EventSummary event = eventSummary("Design session");
        EventListPresenter presenter = new EventListPresenter(
                new StaticEventDataSource(new EventPage(List.of(event), 0, 20, 1)),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                view);

        presenter.load("design");

        assertEquals(List.of("loading", "events"), view.states);
        assertEquals(List.of(event), view.events);
        assertFalse(view.hasMore);
    }

    @Test
    public void appendsTheNextPageWithoutReplacingEarlierResults() {
        EventSummary first = eventSummary("First session");
        EventSummary second = eventSummary("Second session");
        RecordingView view = new RecordingView();
        EventListPresenter presenter = new EventListPresenter(
                new PagedEventDataSource(first, second),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                view);

        presenter.load("");
        presenter.loadMore();

        assertEquals(
                List.of("loading", "events", "loading-more", "events"),
                view.states);
        assertEquals(Arrays.asList(first, second), view.events);
        assertFalse(view.hasMore);
    }

    @Test
    public void showsASafeErrorWhenTheDataSourceFails() {
        RecordingView view = new RecordingView();
        EventListPresenter presenter = new EventListPresenter(
                new FailingEventDataSource(),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                view);

        presenter.load("");

        assertEquals(List.of("loading", "error"), view.states);
        assertEquals("Unable to load events. Check the connection and try again.", view.errorMessage);
    }

    private static EventSummary eventSummary(String title) {
        Instant createdAt = Instant.parse("2026-08-01T12:00:00Z");
        return new EventSummary(
                UUID.randomUUID(),
                title,
                "A working session for the community.",
                Instant.parse("2026-08-10T12:00:00Z"),
                Instant.parse("2026-08-10T13:00:00Z"),
                "Asia/Karachi",
                "Innovation Hub",
                "Workshop",
                "Connexa Community",
                "PUBLISHED",
                0,
                createdAt,
                createdAt);
    }

    private static final class StaticEventDataSource implements EventDataSource {

        private final EventPage page;

        StaticEventDataSource(EventPage page) {
            this.page = page;
        }

        @Override
        public EventPage listPublishedEvents(String query) {
            return page;
        }

        @Override
        public EventSummary getEvent(UUID eventId) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    private static final class PagedEventDataSource implements EventDataSource {

        private final EventSummary first;
        private final EventSummary second;

        PagedEventDataSource(EventSummary first, EventSummary second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public EventPage listPublishedEvents(String query) {
            return new EventPage(List.of(first), 0, 20, 2);
        }

        @Override
        public EventPage listPublishedEvents(String query, int page, int size) {
            if (page == 0) {
                return new EventPage(List.of(first), 0, size, 2);
            }
            if (page == 1) {
                return new EventPage(List.of(second), 1, size, 2);
            }
            return new EventPage(List.of(), page, size, 2);
        }

        @Override
        public EventSummary getEvent(UUID eventId) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    private static final class FailingEventDataSource implements EventDataSource {

        @Override
        public EventPage listPublishedEvents(String query) throws IOException {
            throw new IOException("unavailable");
        }

        @Override
        public EventSummary getEvent(UUID eventId) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    private static final class RecordingView implements EventListPresenter.View {

        private final List<String> states = new ArrayList<>();
        private List<EventSummary> events = List.of();
        private boolean hasMore;
        private String errorMessage;

        @Override
        public void showLoading() {
            states.add("loading");
        }

        @Override
        public void showEvents(List<EventSummary> events, boolean hasMore) {
            states.add("events");
            this.events = List.copyOf(events);
            this.hasMore = hasMore;
        }

        @Override
        public void showEmpty() {
            states.add("empty");
        }

        @Override
        public void showError(String message) {
            states.add("error");
            errorMessage = message;
        }

        @Override
        public void showLoadingMore() {
            states.add("loading-more");
        }

        @Override
        public void showLoadMoreError(String message) {
            states.add("load-more-error");
            errorMessage = message;
        }
    }
}
