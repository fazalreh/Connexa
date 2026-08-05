package com.connexa.mobile.core.events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only boundary for the published-event API.
 *
 * <p>Feature code depends on this interface instead of a particular transport or data vendor.</p>
 */
public interface EventDataSource {

    int MAX_PAGE_SIZE = 100;
    int MAX_EVENT_PAGES_PER_READ = 100;

    EventPage listPublishedEvents(String query) throws IOException;

    /**
     * Loads a requested page. Existing data sources that only expose their first page remain
     * usable; production HTTP clients should override this method to support complete reads.
     */
    default EventPage listPublishedEvents(String query, int page, int size) throws IOException {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (page != 0) {
            throw new IOException("This event data source does not support loading later pages.");
        }
        EventPage result = listPublishedEvents(query);
        if (result.getPage() != 0) {
            throw new IOException("The event data source returned an unexpected page.");
        }
        return result;
    }

    /**
     * Reads a bounded, stable snapshot of every published event matching the query.
     *
     * <p>Calendar and notification features use this method so their result is not silently
     * limited to the first discovery page. Duplicate IDs are collapsed defensively if the
     * underlying catalog changes while pages are being read.</p>
     */
    default List<EventSummary> listAllPublishedEvents(String query) throws IOException {
        Map<UUID, EventSummary> uniqueEvents = new LinkedHashMap<>();
        long expectedTotal = -1L;

        for (int pageIndex = 0; pageIndex < MAX_EVENT_PAGES_PER_READ; pageIndex++) {
            EventPage page = listPublishedEvents(query, pageIndex, MAX_PAGE_SIZE);
            if (page.getPage() != pageIndex) {
                throw new IOException("The event data source returned pages out of order.");
            }
            if (expectedTotal == -1L) {
                expectedTotal = page.getTotal();
            } else if (expectedTotal != page.getTotal()) {
                throw new IOException("The event catalog changed while events were being loaded.");
            }

            for (EventSummary event : page.getItems()) {
                if (event == null) {
                    throw new IOException("The event data source returned an invalid event.");
                }
                uniqueEvents.put(event.getId(), event);
            }

            if (uniqueEvents.size() >= expectedTotal) {
                return Collections.unmodifiableList(new ArrayList<>(uniqueEvents.values()));
            }
            if (page.getItems().isEmpty()) {
                throw new IOException("The event data source ended before the advertised total.");
            }
        }

        throw new IOException("The event catalog exceeds the safe read limit.");
    }

    EventSummary getEvent(UUID eventId) throws IOException;
}
