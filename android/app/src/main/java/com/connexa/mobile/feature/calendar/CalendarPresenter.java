package com.connexa.mobile.feature.calendar;

import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventSummary;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates calendar state and API loading while keeping Android views independent from the
 * transport layer.
 *
 * <p>Call its public methods from the UI thread. The supplied executors determine where network
 * work and rendering callbacks run. A newer refresh or {@link #cancelPendingWork()} suppresses
 * older responses so a stale response cannot replace the currently visible calendar.</p>
 */
public final class CalendarPresenter {

    public interface View {
        void showLoading();

        void showCalendar(CalendarScreen screen);

        void showError(String message);
    }

    private final EventDataSource eventDataSource;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final View view;
    private final CalendarScreenFactory screenFactory;
    private final AtomicLong requestVersion = new AtomicLong();

    private YearMonth visibleMonth;
    private LocalDate selectedDate;
    private List<EventSummary> cachedEvents = Collections.emptyList();
    private boolean hasLoadedEvents;

    public CalendarPresenter(
            EventDataSource eventDataSource,
            Executor backgroundExecutor,
            Executor uiExecutor,
            View view) {
        this(
                eventDataSource,
                backgroundExecutor,
                uiExecutor,
                view,
                Clock.systemDefaultZone(),
                new CalendarScreenFactory());
    }

    public CalendarPresenter(
            EventDataSource eventDataSource,
            Executor backgroundExecutor,
            Executor uiExecutor,
            View view,
            Clock clock,
            CalendarScreenFactory screenFactory) {
        this.eventDataSource = Objects.requireNonNull(eventDataSource, "eventDataSource is required");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor is required");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor is required");
        this.view = Objects.requireNonNull(view, "view is required");
        this.screenFactory = Objects.requireNonNull(screenFactory, "screenFactory is required");
        Objects.requireNonNull(clock, "clock is required");
        this.selectedDate = LocalDate.now(clock);
        this.visibleMonth = YearMonth.from(selectedDate);
    }

    /** Loads all published events needed for calendar display. */
    public void load() {
        long version = requestVersion.incrementAndGet();
        view.showLoading();
        backgroundExecutor.execute(() -> {
            try {
                List<EventSummary> allEvents = eventDataSource.listAllPublishedEvents("");
                List<EventSummary> events = Collections.unmodifiableList(
                        new ArrayList<>(allEvents));
                uiExecutor.execute(() -> renderLoadedEvents(version, events));
            } catch (IOException | RuntimeException exception) {
                uiExecutor.execute(() -> renderFailure(version));
            }
        });
    }

    /** Reloads event summaries after a user-triggered refresh. */
    public void refresh() {
        load();
    }

    /** Selects a day and moves the visible month when necessary. */
    public void selectDate(LocalDate date) {
        this.selectedDate = Objects.requireNonNull(date, "date is required");
        this.visibleMonth = YearMonth.from(date);
        renderCachedCalendarIfAvailable();
    }

    /** Moves to the preceding month while preserving the closest valid day number. */
    public void selectPreviousMonth() {
        moveToMonth(visibleMonth.minusMonths(1));
    }

    /** Moves to the following month while preserving the closest valid day number. */
    public void selectNextMonth() {
        moveToMonth(visibleMonth.plusMonths(1));
    }

    /** Prevents callbacks from work started before the current UI owner was stopped. */
    public void cancelPendingWork() {
        requestVersion.incrementAndGet();
    }

    private void moveToMonth(YearMonth targetMonth) {
        visibleMonth = targetMonth;
        selectedDate = targetMonth.atDay(Math.min(selectedDate.getDayOfMonth(), targetMonth.lengthOfMonth()));
        renderCachedCalendarIfAvailable();
    }

    private void renderLoadedEvents(long version, List<EventSummary> events) {
        if (version != requestVersion.get()) {
            return;
        }
        cachedEvents = events;
        hasLoadedEvents = true;
        view.showCalendar(screenFactory.create(visibleMonth, selectedDate, cachedEvents));
    }

    private void renderFailure(long version) {
        if (version == requestVersion.get()) {
            view.showError("Unable to load the calendar. Check the connection and try again.");
        }
    }

    private void renderCachedCalendarIfAvailable() {
        if (hasLoadedEvents) {
            view.showCalendar(screenFactory.create(visibleMonth, selectedDate, cachedEvents));
        }
    }
}
