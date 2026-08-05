package com.connexa.mobile.feature.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.connexa.mobile.core.assistant.AssistantConversation;
import com.connexa.mobile.core.assistant.AssistantDataSource;
import com.connexa.mobile.core.assistant.AssistantMessage;
import com.connexa.mobile.core.assistant.AssistantMessageRole;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.Test;

public class AssistantPresenterTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    public void startsConversationAndClearsComposerAfterSuccessfulResponse() {
        RecordingView view = new RecordingView();
        RecordingDataSource dataSource = new RecordingDataSource(conversation(UUID.randomUUID()));
        AssistantPresenter presenter = new AssistantPresenter(
                dataSource, DIRECT_EXECUTOR, DIRECT_EXECUTOR, view);

        presenter.submit("  Which workshops are happening this week?  ");

        assertEquals("Which workshops are happening this week?", dataSource.startMessage);
        assertEquals(
                List.of("sending", "composer:false", "conversation", "clear", "composer:true"),
                view.states);
        assertEquals(dataSource.response, view.conversation);
    }

    @Test
    public void rejectsBlankInputWithoutCallingTheDataSource() {
        RecordingView view = new RecordingView();
        RecordingDataSource dataSource = new RecordingDataSource(conversation(UUID.randomUUID()));
        AssistantPresenter presenter = new AssistantPresenter(
                dataSource, DIRECT_EXECUTOR, DIRECT_EXECUTOR, view);

        presenter.submit("   ");

        assertEquals(List.of("inputError"), view.states);
        assertEquals("Enter a message before sending.", view.inputError);
        assertNull(dataSource.startMessage);
        assertNull(dataSource.continueMessage);
    }

    @Test
    public void keepsBackendFailureDetailsOutOfTheUi() {
        RecordingView view = new RecordingView();
        AssistantDataSource dataSource = new AssistantDataSource() {
            @Override
            public AssistantConversation startConversation(String message) throws IOException {
                throw new IOException("backend topology should not be exposed");
            }

            @Override
            public AssistantConversation continueConversation(UUID conversationId, String message) {
                throw new AssertionError("not used");
            }
        };
        AssistantPresenter presenter = new AssistantPresenter(
                dataSource, DIRECT_EXECUTOR, DIRECT_EXECUTOR, view);

        presenter.submit("What events are available?");

        assertEquals(
                List.of("sending", "composer:false", "error", "composer:true"),
                view.states);
        assertEquals(
                "We couldn't send that message. Check the connection and try again.",
                view.error);
    }

    @Test
    public void continuesTheRestoredConversation() {
        UUID conversationId = UUID.randomUUID();
        AssistantConversation restoredConversation = emptyConversation(conversationId);
        AssistantConversation updatedConversation = conversation(conversationId);
        RecordingView view = new RecordingView();
        RecordingDataSource dataSource = new RecordingDataSource(updatedConversation);
        AssistantPresenter presenter = new AssistantPresenter(
                dataSource, DIRECT_EXECUTOR, DIRECT_EXECUTOR, view);

        presenter.restoreConversation(restoredConversation);
        view.reset();
        presenter.submit("Is registration still open?");

        assertEquals(conversationId, dataSource.continueConversationId);
        assertEquals("Is registration still open?", dataSource.continueMessage);
        assertEquals(updatedConversation, view.conversation);
    }

    @Test
    public void ignoresAResponseAfterPendingWorkIsCancelled() {
        QueueExecutor backgroundExecutor = new QueueExecutor();
        RecordingView view = new RecordingView();
        RecordingDataSource dataSource = new RecordingDataSource(conversation(UUID.randomUUID()));
        AssistantPresenter presenter = new AssistantPresenter(
                dataSource, backgroundExecutor, DIRECT_EXECUTOR, view);

        presenter.submit("What is next?");
        presenter.cancelPendingWork();
        backgroundExecutor.runNext();

        assertEquals(List.of("sending", "composer:false"), view.states);
        assertNull(view.conversation);
    }

    private static AssistantConversation emptyConversation(UUID conversationId) {
        Instant createdAt = Instant.parse("2026-08-05T12:00:00Z");
        return new AssistantConversation(conversationId, createdAt, createdAt, List.of());
    }

    private static AssistantConversation conversation(UUID conversationId) {
        Instant createdAt = Instant.parse("2026-08-05T12:00:00Z");
        AssistantMessage userMessage = new AssistantMessage(
                UUID.randomUUID(),
                conversationId,
                AssistantMessageRole.USER,
                "Which workshops are happening this week?",
                List.of(),
                createdAt);
        AssistantMessage assistantMessage = new AssistantMessage(
                UUID.randomUUID(),
                conversationId,
                AssistantMessageRole.ASSISTANT,
                "A design workshop is scheduled for Thursday.",
                List.of(),
                createdAt.plusSeconds(1));
        return new AssistantConversation(
                conversationId,
                createdAt,
                createdAt.plusSeconds(1),
                List.of(userMessage, assistantMessage));
    }

    private static final class RecordingDataSource implements AssistantDataSource {

        private final AssistantConversation response;
        private String startMessage;
        private UUID continueConversationId;
        private String continueMessage;

        RecordingDataSource(AssistantConversation response) {
            this.response = response;
        }

        @Override
        public AssistantConversation startConversation(String message) {
            startMessage = message;
            return response;
        }

        @Override
        public AssistantConversation continueConversation(UUID conversationId, String message) {
            continueConversationId = conversationId;
            continueMessage = message;
            return response;
        }
    }

    private static final class RecordingView implements AssistantPresenter.View {

        private final List<String> states = new ArrayList<>();
        private AssistantConversation conversation;
        private String inputError;
        private String error;

        @Override
        public void showSending() {
            states.add("sending");
        }

        @Override
        public void showConversation(AssistantConversation conversation) {
            states.add("conversation");
            this.conversation = conversation;
        }

        @Override
        public void showInputError(String message) {
            states.add("inputError");
            inputError = message;
        }

        @Override
        public void showError(String message) {
            states.add("error");
            error = message;
        }

        @Override
        public void clearComposer() {
            states.add("clear");
        }

        @Override
        public void setComposerEnabled(boolean enabled) {
            states.add("composer:" + enabled);
        }

        void reset() {
            states.clear();
            conversation = null;
            inputError = null;
            error = null;
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
