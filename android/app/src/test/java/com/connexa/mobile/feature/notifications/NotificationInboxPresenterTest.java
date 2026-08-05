package com.connexa.mobile.feature.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.notifications.NotificationDataSource;
import com.connexa.mobile.core.notifications.NotificationItem;
import com.connexa.mobile.core.notifications.NotificationType;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.Test;

public class NotificationInboxPresenterTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    public void rendersContentOrderedByNewestNotificationFirst() {
        NotificationItem older = notification("2059a4a3-aa41-487a-ab8b-bbe1c4cc5fa4", "2026-08-05T10:00:00Z");
        NotificationItem newer = notification("095898ab-d7e3-47fe-9b99-4864a454ed7d", "2026-08-05T12:00:00Z");
        RecordingView view = new RecordingView();
        NotificationInboxPresenter presenter = new NotificationInboxPresenter(
                () -> Arrays.asList(older, newer), DIRECT_EXECUTOR, DIRECT_EXECUTOR, view);

        presenter.load();

        assertEquals(
                Arrays.asList(
                        NotificationInboxState.Status.LOADING,
                        NotificationInboxState.Status.CONTENT),
                view.statuses());
        assertEquals(newer, view.lastState().getNotifications().get(0));
        assertEquals(older, view.lastState().getNotifications().get(1));
    }

    @Test
    public void rendersAnEmptyStateWhenTheInboxHasNoItems() {
        RecordingView view = new RecordingView();
        NotificationInboxPresenter presenter = new NotificationInboxPresenter(
                Collections::emptyList, DIRECT_EXECUTOR, DIRECT_EXECUTOR, view);

        presenter.load();

        assertEquals(NotificationInboxState.Status.EMPTY, view.lastState().getStatus());
    }

    @Test
    public void keepsBackendFailureDetailsOutOfTheUi() {
        RecordingView view = new RecordingView();
        NotificationDataSource dataSource = new NotificationDataSource() {
            @Override
            public List<NotificationItem> listNotifications() throws IOException {
                throw new IOException("internal endpoint details");
            }
        };
        NotificationInboxPresenter presenter = new NotificationInboxPresenter(
                dataSource, DIRECT_EXECUTOR, DIRECT_EXECUTOR, view);

        presenter.load();

        assertEquals(NotificationInboxState.Status.ERROR, view.lastState().getStatus());
        assertEquals(
                "Unable to load notifications. Check the connection and try again.",
                view.lastState().getErrorMessage());
    }

    @Test
    public void ignoresAResponseAfterPendingWorkIsCancelled() {
        QueueExecutor backgroundExecutor = new QueueExecutor();
        RecordingView view = new RecordingView();
        NotificationInboxPresenter presenter = new NotificationInboxPresenter(
                () -> Collections.singletonList(notification(
                        "baf2e157-f244-488d-b0c0-efb14b3bda33", "2026-08-05T12:00:00Z")),
                backgroundExecutor,
                DIRECT_EXECUTOR,
                view);

        presenter.load();
        presenter.cancelPendingWork();
        backgroundExecutor.runNext();

        assertEquals(1, view.states.size());
        assertEquals(NotificationInboxState.Status.LOADING, view.lastState().getStatus());
    }

    @Test
    public void contentStateDoesNotExposeAMutableList() {
        RecordingView view = new RecordingView();
        NotificationInboxPresenter presenter = new NotificationInboxPresenter(
                () -> Collections.singletonList(notification(
                        "8ce054d6-57dd-478c-a4aa-2e1661a5e785", "2026-08-05T12:00:00Z")),
                DIRECT_EXECUTOR,
                DIRECT_EXECUTOR,
                view);

        presenter.load();

        try {
            view.lastState().getNotifications().clear();
        } catch (UnsupportedOperationException expected) {
            assertTrue(view.lastState().getNotifications().size() == 1);
            return;
        }
        throw new AssertionError("Notification state must be immutable");
    }

    private static NotificationItem notification(String id, String occurredAt) {
        return new NotificationItem(
                UUID.fromString(id),
                NotificationType.EVENT_REMINDER,
                "Event reminder",
                "Design Studio starts soon.",
                UUID.fromString("908a87ee-daf7-471c-91fe-fc0cfd2af304"),
                Instant.parse(occurredAt),
                false);
    }

    private static final class RecordingView implements NotificationInboxPresenter.View {

        private final List<NotificationInboxState> states = new ArrayList<>();

        @Override
        public void render(NotificationInboxState state) {
            states.add(state);
        }

        List<NotificationInboxState.Status> statuses() {
            ArrayList<NotificationInboxState.Status> statuses = new ArrayList<>(states.size());
            for (NotificationInboxState state : states) {
                statuses.add(state.getStatus());
            }
            return statuses;
        }

        NotificationInboxState lastState() {
            return states.get(states.size() - 1);
        }
    }

    private static final class QueueExecutor implements Executor {

        private Runnable queuedTask;

        @Override
        public void execute(Runnable command) {
            queuedTask = command;
        }

        void runNext() {
            Runnable task = queuedTask;
            queuedTask = null;
            task.run();
        }
    }
}
