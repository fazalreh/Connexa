package com.connexa.mobile.core.events;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public final class EventDataSourcePaginationTest {

    @Test
    public void readsEveryPublishedEventAcrossPages() throws IOException {
        List<EventSummary> events = new ArrayList<>();
        for (int index = 0; index < 205; index++) {
            events.add(event(index));
        }
        PagedDataSource source = new PagedDataSource(events);

        List<EventSummary> result = source.listAllPublishedEvents("");

        assertEquals(205, result.size());
        assertEquals(Arrays.asList(0, 1, 2), source.requestedPages);
    }

    private static EventSummary event(int index) {
        Instant timestamp = Instant.parse("2026-08-05T10:00:00Z");
        return new EventSummary(
                UUID.randomUUID(),
                "Event " + index,
                "A complete event description for pagination tests.",
                timestamp,
                timestamp.plusSeconds(3_600),
                "UTC",
                "Community room",
                "Community",
                "Connexa",
                "PUBLISHED",
                0,
                timestamp,
                timestamp);
    }

    private static final class PagedDataSource implements EventDataSource {

        private final List<EventSummary> events;
        private final List<Integer> requestedPages = new ArrayList<>();

        PagedDataSource(List<EventSummary> events) {
            this.events = events;
        }

        @Override
        public EventPage listPublishedEvents(String query) {
            return page(0, 20);
        }

        @Override
        public EventPage listPublishedEvents(String query, int page, int size) {
            requestedPages.add(page);
            return page(page, size);
        }

        @Override
        public EventSummary getEvent(UUID eventId) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        private EventPage page(int page, int size) {
            int start = page * size;
            int end = Math.min(start + size, events.size());
            List<EventSummary> items = start >= events.size()
                    ? new ArrayList<>()
                    : new ArrayList<>(events.subList(start, end));
            return new EventPage(items, page, size, events.size());
        }
    }
}
