package com.connexa.mobile.feature.events;

import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventPage;
import com.connexa.mobile.core.events.EventSummary;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates paginated event discovery without exposing transport details to the activity.
 *
 * <p>Calls are expected from the UI thread. A newer search invalidates any older response, so an
 * out-of-date page can never replace the current search results.</p>
 */
public final class EventListPresenter {

    public static final int PAGE_SIZE = 20;

    public interface View {
        void showLoading();

        void showEvents(List<EventSummary> events, boolean hasMore);

        void showEmpty();

        void showError(String message);

        void showLoadingMore();

        void showLoadMoreError(String message);
    }

    private static final String LOAD_ERROR =
            "Unable to load events. Check the connection and try again.";

    private final EventDataSource eventDataSource;
    /** Set only when the data source can do meaning-based search. */
    private final com.connexa.mobile.core.network.EventApiClient searchable;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final View view;
    private final AtomicLong requestVersion = new AtomicLong();
    private final Map<UUID, EventSummary> loadedEvents = new LinkedHashMap<>();

    private String activeQuery = "";
    private long expectedTotal;
    private int nextPage;
    private boolean hasMore;
    private boolean requestInFlight;

    public EventListPresenter(
            EventDataSource eventDataSource,
            Executor backgroundExecutor,
            Executor uiExecutor,
            View view) {
        this.eventDataSource = Objects.requireNonNull(eventDataSource, "eventDataSource is required");
        this.searchable = eventDataSource instanceof com.connexa.mobile.core.network.EventApiClient client
                ? client
                : null;
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor is required");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor is required");
        this.view = Objects.requireNonNull(view, "view is required");
    }

    /** Starts a new search and discards the previous result set. */
    public void load(String query) {
        long version = requestVersion.incrementAndGet();
        activeQuery = normalizeQuery(query);
        loadedEvents.clear();
        expectedTotal = 0;
        nextPage = 0;
        hasMore = false;
        requestInFlight = true;
        view.showLoading();
        requestPage(version, true, activeQuery, 0);
    }

    /** Requests the next available page for the current search. */
    public void loadMore() {
        if (!hasMore || requestInFlight) {
            return;
        }
        long version = requestVersion.incrementAndGet();
        requestInFlight = true;
        view.showLoadingMore();
        requestPage(version, false, activeQuery, nextPage);
    }

    /** Prevents callbacks from a stopped screen from changing visible state. */
    public void cancelPendingWork() {
        requestVersion.incrementAndGet();
        requestInFlight = false;
    }

    private static boolean hasPhrase(String query) {
        return query != null && query.trim().length() >= 2;
    }

    private void requestPage(long version, boolean initialLoad, String query, int page) {
        try {
            backgroundExecutor.execute(() -> {
                try {
                    // A typed phrase goes through meaning-based search; browsing with no
                    // phrase stays on the paged catalogue, which search does not paginate.
                    EventPage eventPage = searchable != null && page == 0 && hasPhrase(query)
                            ? searchable.searchEvents(query, PAGE_SIZE)
                            : eventDataSource.listPublishedEvents(query, page, PAGE_SIZE);
                    dispatchPage(version, initialLoad, page, eventPage);
                } catch (IOException | RuntimeException exception) {
                    dispatchFailure(version, initialLoad);
                }
            });
        } catch (RuntimeException exception) {
            dispatchFailure(version, initialLoad);
        }
    }

    private void dispatchPage(long version, boolean initialLoad, int requestedPage, EventPage eventPage) {
        try {
            uiExecutor.execute(() -> renderPage(version, initialLoad, requestedPage, eventPage));
        } catch (RuntimeException ignored) {
            // The UI owner may already be stopped. There is no visible state left to update.
        }
    }

    private void dispatchFailure(long version, boolean initialLoad) {
        try {
            uiExecutor.execute(() -> renderFailure(version, initialLoad));
        } catch (RuntimeException ignored) {
            // The UI owner may already be stopped. There is no visible state left to update.
        }
    }

    private void renderPage(
            long version,
            boolean initialLoad,
            int requestedPage,
            EventPage eventPage) {
        if (version != requestVersion.get()) {
            return;
        }
        requestInFlight = false;
        if (eventPage == null || eventPage.getPage() != requestedPage) {
            renderFailure(version, initialLoad);
            return;
        }

        if (initialLoad) {
            expectedTotal = eventPage.getTotal();
        } else if (expectedTotal != eventPage.getTotal()) {
            renderFailure(version, false);
            return;
        }

        for (EventSummary event : eventPage.getItems()) {
            if (event == null) {
                renderFailure(version, initialLoad);
                return;
            }
            loadedEvents.put(event.getId(), event);
        }

        nextPage = requestedPage + 1;
        hasMore = loadedEvents.size() < expectedTotal;
        if (hasMore && eventPage.getItems().isEmpty()) {
            renderFailure(version, initialLoad);
            return;
        }

        if (loadedEvents.isEmpty()) {
            view.showEmpty();
            return;
        }
        view.showEvents(immutableEvents(), hasMore);
    }

    private void renderFailure(long version, boolean initialLoad) {
        if (version != requestVersion.get()) {
            return;
        }
        requestInFlight = false;
        if (initialLoad) {
            view.showError(LOAD_ERROR);
        } else {
            view.showLoadMoreError(LOAD_ERROR);
        }
    }

    private List<EventSummary> immutableEvents() {
        return Collections.unmodifiableList(new ArrayList<>(loadedEvents.values()));
    }

    private static String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }
}
