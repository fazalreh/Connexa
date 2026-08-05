package com.connexa.mobile.feature.notifications;

import com.connexa.mobile.core.notifications.NotificationItem;
import java.util.Objects;

/**
 * A stable row model for a grouped notification inbox.
 */
abstract class NotificationInboxRow {

    static final int VIEW_TYPE_SECTION = 1;
    static final int VIEW_TYPE_NOTIFICATION = 2;

    abstract int getViewType();

    static NotificationInboxRow section(String title) {
        return new SectionRow(title);
    }

    static NotificationInboxRow notification(NotificationItem notification) {
        return new NotificationRow(notification);
    }

    static final class SectionRow extends NotificationInboxRow {

        private final String title;

        SectionRow(String title) {
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("title is required");
            }
            this.title = title.trim();
        }

        String getTitle() {
            return title;
        }

        @Override
        int getViewType() {
            return VIEW_TYPE_SECTION;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SectionRow && title.equals(((SectionRow) other).title);
        }

        @Override
        public int hashCode() {
            return title.hashCode();
        }
    }

    static final class NotificationRow extends NotificationInboxRow {

        private final NotificationItem notification;

        NotificationRow(NotificationItem notification) {
            this.notification = Objects.requireNonNull(notification, "notification is required");
        }

        NotificationItem getNotification() {
            return notification;
        }

        @Override
        int getViewType() {
            return VIEW_TYPE_NOTIFICATION;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NotificationRow
                    && notification.equals(((NotificationRow) other).notification);
        }

        @Override
        public int hashCode() {
            return notification.hashCode();
        }
    }
}
