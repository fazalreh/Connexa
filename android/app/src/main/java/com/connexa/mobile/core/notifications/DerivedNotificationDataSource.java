package com.connexa.mobile.core.notifications;

import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read model used until the authenticated notification endpoint is configured.
 *
 * <p>It derives only upcoming-event notices from public event summaries. RSVP confirmations and
 * reminders require the authenticated notification API and are therefore not synthesized here.</p>
 */
public final class DerivedNotificationDataSource implements NotificationDataSource {

    private static final Duration UPCOMING_WINDOW = Duration.ofDays(7);

    private final EventDataSource eventDataSource;
    private final Clock clock;

    public DerivedNotificationDataSource(EventDataSource eventDataSource, Clock clock) {
        this.eventDataSource = Objects.requireNonNull(eventDataSource, "eventDataSource is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public List<NotificationItem> listNotifications() throws IOException {
        Instant now = clock.instant();
        Instant windowEnd = now.plus(UPCOMING_WINDOW);
        List<NotificationItem> notices = new ArrayList<>();
        for (EventSummary event : eventDataSource.listAllPublishedEvents("")) {
            if (!event.getStartsAt().isBefore(now) && !event.getStartsAt().isAfter(windowEnd)) {
                notices.add(new NotificationItem(
                        notificationIdFor(event),
                        NotificationType.NEW_EVENT,
                        "Upcoming event",
                        event.getTitle() + " is happening at " + event.getVenueName() + ".",
                        event.getId(),
                        event.getUpdatedAt(),
                        false));
            }
        }
        return Collections.unmodifiableList(notices);
    }

    private static UUID notificationIdFor(EventSummary event) {
        return UUID.nameUUIDFromBytes(
                ("connexa-event-notice:" + event.getId()).getBytes(StandardCharsets.UTF_8));
    }
}
