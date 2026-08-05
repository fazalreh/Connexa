package com.connexa.mobile.feature.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventPage;
import com.connexa.mobile.core.events.EventSummary;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.Test;

public final class CalendarPresenterTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    public void loadsPublishedEventsAndRendersTheCurrentMonth() {
        EventSummary event = event("PUBLISHED", "2026-02-14T10:00:00Z", "2026-02-14T11:00:00Z");
        RecordingView view = new RecordingView();
        CalendarPresenter presenter = new CalendarPresenter(
                new FixedEventDataSource(Arrays.asList(event)),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                view,
                Clock.fixed(Instant.parse("2026-02-14T06:00:00Z"), ZoneOffset.UTC),
                new CalendarScreenFactory());

        presenter.load();

        assertTrue(view.loadingShown);
        assertNotNull(view.screen);
        assertEquals(LocalDate.of(2026, 2, 14), view.screen.getSelectedDate());
        assertEquals(1, view.screen.getSelectedDateEvents().size());
        assertNull(view.error);
    }

    @Test
    public void preservesClosestValidDayWhenChangingMonth() {
        RecordingView view = new RecordingView();
        CalendarPresenter presenter = new CalendarPresenter(
                new FixedEventDataSource(Collections.emptyList()),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                view,
                Clock.fixed(Instant.parse("2026-01-31T12:00:00Z"), ZoneOffset.UTC),
                new CalendarScreenFactory());

        presenter.load();
        presenter.selectNextMonth();

        assertNotNull(view.screen);
        assertEquals(LocalDate.of(2026, 2, 28), view.screen.getSelectedDate());
        assertEquals(2, view.screen.getVisibleMonth().getMonthValue());
    }

    @Test
    public void ignoresResponseAfterPendingWorkIsCancelled() {
        List<Runnable> queuedWork = new ArrayList<>();
        Executor queuedExecutor = queuedWork::add;
        RecordingView view = new RecordingView();
        CalendarPresenter presenter = new CalendarPresenter(
                new FixedEventDataSource(Collections.emptyList()),
                queuedExecutor,
                DIRECT_EXECUTOR,
                view,
                Clock.fixed(Instant.parse("2026-01-31T12:00:00Z"), ZoneOffset.UTC),
                new CalendarScreenFactory());

        presenter.load();
        presenter.cancelPendingWork();
        queuedWork.get(0).run();

        assertFalse(view.hasCalendar());
        assertNull(view.error);
    }

    private static EventSummary event(String status, String startsAt, String endsAt) {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new EventSummary(
                UUID.randomUUID(),
                "Town hall",
                "A calendar presenter test event",
                Instant.parse(startsAt),
                Instant.parse(endsAt),
                "UTC",
                "Community room",
                "Discussion",
                "Connexa",
                status,
                0,
                createdAt,
                createdAt);
    }

    private static final class FixedEventDataSource implements EventDataSource {

        private final List<EventSummary> events;

        FixedEventDataSource(List<EventSummary> events) {
            this.events = events;
        }

        @Override
        public EventPage listPublishedEvents(String query) throws IOException {
            return new EventPage(events, 0, 20, events.size());
        }

        @Override
        public EventSummary getEvent(UUID eventId) throws IOException {
            throw new IOException("Not used by calendar tests");
        }
    }

    private static final class RecordingView implements CalendarPresenter.View {

        private boolean loadingShown;
        private CalendarScreen screen;
        private String error;

        @Override
        public void showLoading() {
            loadingShown = true;
        }

        @Override
        public void showCalendar(CalendarScreen screen) {
            this.screen = screen;
        }

        @Override
        public void showError(String message) {
            error = message;
        }

        boolean hasCalendar() {
            return screen != null;
        }
    }
}
