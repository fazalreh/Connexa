package com.connexa.mobile.feature.notifications;

import com.connexa.mobile.core.notifications.NotificationItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable state rendered by a notification-inbox screen.
 */
public final class NotificationInboxState {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    private static final NotificationInboxState LOADING = new NotificationInboxState(
            Status.LOADING, Collections.emptyList(), null);
    private static final NotificationInboxState EMPTY = new NotificationInboxState(
            Status.EMPTY, Collections.emptyList(), null);

    private final Status status;
    private final List<NotificationItem> notifications;
    private final String errorMessage;

    private NotificationInboxState(
            Status status,
            List<NotificationItem> notifications,
            String errorMessage) {
        this.status = Objects.requireNonNull(status, "status is required");
        this.notifications = immutableCopy(notifications);
        this.errorMessage = errorMessage;
    }

    public static NotificationInboxState loading() {
        return LOADING;
    }

    public static NotificationInboxState content(List<NotificationItem> notifications) {
        List<NotificationItem> copiedNotifications = immutableCopy(notifications);
        if (copiedNotifications.isEmpty()) {
            throw new IllegalArgumentException("content requires at least one notification");
        }
        return new NotificationInboxState(Status.CONTENT, copiedNotifications, null);
    }

    public static NotificationInboxState empty() {
        return EMPTY;
    }

    public static NotificationInboxState error(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("errorMessage is required");
        }
        return new NotificationInboxState(Status.ERROR, Collections.emptyList(), errorMessage.trim());
    }

    public Status getStatus() {
        return status;
    }

    public List<NotificationItem> getNotifications() {
        return notifications;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private static List<NotificationItem> immutableCopy(List<NotificationItem> notifications) {
        Objects.requireNonNull(notifications, "notifications is required");
        ArrayList<NotificationItem> copiedNotifications = new ArrayList<>(notifications.size());
        for (NotificationItem notification : notifications) {
            copiedNotifications.add(Objects.requireNonNull(
                    notification, "notifications cannot contain null items"));
        }
        return Collections.unmodifiableList(copiedNotifications);
    }
}
