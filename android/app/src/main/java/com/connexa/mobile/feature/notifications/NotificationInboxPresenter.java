package com.connexa.mobile.feature.notifications;

import com.connexa.mobile.core.notifications.NotificationDataSource;
import com.connexa.mobile.core.notifications.NotificationItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loads a notification inbox and exposes explicit render states to the UI.
 */
public final class NotificationInboxPresenter {

    private static final String LOAD_ERROR_MESSAGE =
            "Unable to load notifications. Check the connection and try again.";

    public interface View {
        void render(NotificationInboxState state);
    }

    private final NotificationDataSource notificationDataSource;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final View view;
    private final AtomicLong requestVersion = new AtomicLong();

    public NotificationInboxPresenter(
            NotificationDataSource notificationDataSource,
            Executor backgroundExecutor,
            Executor uiExecutor,
            View view) {
        this.notificationDataSource = Objects.requireNonNull(
                notificationDataSource, "notificationDataSource is required");
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor, "backgroundExecutor is required");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor is required");
        this.view = Objects.requireNonNull(view, "view is required");
    }

    /**
     * Requests the latest inbox contents. A stale response is ignored when a newer request starts.
     */
    public void load() {
        long version = requestVersion.incrementAndGet();
        view.render(NotificationInboxState.loading());
        try {
            backgroundExecutor.execute(() -> requestInbox(version));
        } catch (RuntimeException exception) {
            dispatchFailure(version);
        }
    }

    /**
     * Invalidates callbacks after the hosting screen is no longer active.
     */
    public void cancelPendingWork() {
        requestVersion.incrementAndGet();
    }

    private void requestInbox(long version) {
        try {
            List<NotificationItem> notifications = notificationDataSource.listNotifications();
            List<NotificationItem> orderedNotifications = orderByNewestFirst(notifications);
            uiExecutor.execute(() -> renderInbox(version, orderedNotifications));
        } catch (IOException | RuntimeException exception) {
            dispatchFailure(version);
        }
    }

    private void renderInbox(long version, List<NotificationItem> notifications) {
        if (version != requestVersion.get()) {
            return;
        }
        if (notifications.isEmpty()) {
            view.render(NotificationInboxState.empty());
            return;
        }
        view.render(NotificationInboxState.content(notifications));
    }

    private void dispatchFailure(long version) {
        try {
            uiExecutor.execute(() -> {
                if (version == requestVersion.get()) {
                    view.render(NotificationInboxState.error(LOAD_ERROR_MESSAGE));
                }
            });
        } catch (RuntimeException ignored) {
            // The host may have stopped before the callback could be delivered.
        }
    }

    private static List<NotificationItem> orderByNewestFirst(List<NotificationItem> notifications) {
        Objects.requireNonNull(notifications, "notifications is required");
        ArrayList<NotificationItem> orderedNotifications = new ArrayList<>(notifications.size());
        for (NotificationItem notification : notifications) {
            orderedNotifications.add(Objects.requireNonNull(
                    notification, "notifications cannot contain null items"));
        }
        Collections.sort(orderedNotifications, new Comparator<NotificationItem>() {
            @Override
            public int compare(NotificationItem first, NotificationItem second) {
                int timestampComparison = second.getOccurredAt().compareTo(first.getOccurredAt());
                if (timestampComparison != 0) {
                    return timestampComparison;
                }
                return first.getId().compareTo(second.getId());
            }
        });
        return Collections.unmodifiableList(orderedNotifications);
    }
}
