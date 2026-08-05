package com.connexa.mobile.core.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stable, paginated response for published events.
 */
public final class EventPage {

    private final List<EventSummary> items;
    private final int page;
    private final int size;
    private final long total;

    public EventPage(List<EventSummary> items, int page, int size, long total) {
        this.items = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(items, "items is required")));
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (total < this.items.size()) {
            throw new IllegalArgumentException("total cannot be smaller than the returned item count");
        }
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<EventSummary> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
