package com.connexa.mobile.feature.notifications;

import com.connexa.mobile.core.notifications.NotificationItem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Creates the recent and earlier sections rendered by the inbox adapter.
 */
final class NotificationInboxRows {

    private static final Duration RECENT_WINDOW = Duration.ofHours(24);
    private static final String RECENT_SECTION_TITLE = "Notifications";
    private static final String EARLIER_SECTION_TITLE = "Earlier";

    private NotificationInboxRows() {
    }

    static List<NotificationInboxRow> from(List<NotificationItem> notifications, Clock clock) {
        Objects.requireNonNull(notifications, "notifications is required");
        Objects.requireNonNull(clock, "clock is required");

        ArrayList<NotificationItem> recentNotifications = new ArrayList<>();
        ArrayList<NotificationItem> earlierNotifications = new ArrayList<>();
        Instant recentBoundary = clock.instant().minus(RECENT_WINDOW);

        for (NotificationItem notification : notifications) {
            NotificationItem nonNullNotification = Objects.requireNonNull(
                    notification, "notifications cannot contain null items");
            if (nonNullNotification.getOccurredAt().isBefore(recentBoundary)) {
                earlierNotifications.add(nonNullNotification);
            } else {
                recentNotifications.add(nonNullNotification);
            }
        }

        sortNewestFirst(recentNotifications);
        sortNewestFirst(earlierNotifications);

        ArrayList<NotificationInboxRow> rows = new ArrayList<>(
                notifications.size() + (recentNotifications.isEmpty() || earlierNotifications.isEmpty() ? 1 : 2));
        appendSection(rows, RECENT_SECTION_TITLE, recentNotifications);
        appendSection(rows, EARLIER_SECTION_TITLE, earlierNotifications);
        return Collections.unmodifiableList(rows);
    }

    private static void appendSection(
            List<NotificationInboxRow> rows,
            String title,
            List<NotificationItem> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        rows.add(NotificationInboxRow.section(title));
        for (NotificationItem notification : notifications) {
            rows.add(NotificationInboxRow.notification(notification));
        }
    }

    private static void sortNewestFirst(List<NotificationItem> notifications) {
        Collections.sort(notifications, new Comparator<NotificationItem>() {
            @Override
            public int compare(NotificationItem first, NotificationItem second) {
                int timestampComparison = second.getOccurredAt().compareTo(first.getOccurredAt());
                if (timestampComparison != 0) {
                    return timestampComparison;
                }
                return first.getId().compareTo(second.getId());
            }
        });
    }
}
