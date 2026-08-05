package com.connexa.mobile.feature.assistant;

import com.connexa.mobile.core.assistant.AssistantConversation;
import com.connexa.mobile.core.assistant.AssistantDataSource;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates one event-assistant conversation while keeping backend details out of the UI.
 */
public final class AssistantPresenter {

    public static final int MAX_USER_MESSAGE_LENGTH = 1_000;

    private static final String EMPTY_MESSAGE_ERROR = "Enter a message before sending.";
    private static final String TOO_LONG_MESSAGE_ERROR = "Messages can contain up to 1,000 characters.";
    private static final String UNSUPPORTED_CHARACTER_ERROR =
            "That message contains unsupported characters.";
    private static final String REQUEST_IN_PROGRESS_ERROR = "A message is already being sent.";
    private static final String GENERIC_REQUEST_ERROR =
            "We couldn't send that message. Check the connection and try again.";

    public interface View {
        void showSending();

        void showConversation(AssistantConversation conversation);

        void showInputError(String message);

        void showError(String message);

        void clearComposer();

        void setComposerEnabled(boolean enabled);
    }

    private final AssistantDataSource assistantDataSource;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final View view;
    private final AtomicLong requestVersion = new AtomicLong();
    private final AtomicBoolean requestInFlight = new AtomicBoolean();
    private final AtomicReference<UUID> conversationId = new AtomicReference<>();

    public AssistantPresenter(
            AssistantDataSource assistantDataSource,
            Executor backgroundExecutor,
            Executor uiExecutor,
            View view) {
        this.assistantDataSource = Objects.requireNonNull(
                assistantDataSource, "assistantDataSource is required");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor is required");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor is required");
        this.view = Objects.requireNonNull(view, "view is required");
    }

    /**
     * Validates and sends a user message. The composer is cleared only after the backend confirms
     * a valid response.
     */
    public void submit(String rawMessage) {
        String message = validateAndNormalize(rawMessage);
        if (message == null) {
            return;
        }
        if (!requestInFlight.compareAndSet(false, true)) {
            view.showInputError(REQUEST_IN_PROGRESS_ERROR);
            return;
        }

        long version = requestVersion.incrementAndGet();
        UUID requestedConversationId = conversationId.get();
        view.showSending();
        view.setComposerEnabled(false);

        try {
            backgroundExecutor.execute(() -> requestConversation(version, requestedConversationId, message));
        } catch (RuntimeException exception) {
            dispatchFailure(version);
        }
    }

    /**
     * Restores a backend-issued conversation without making a network request.
     */
    public void restoreConversation(AssistantConversation conversation) {
        AssistantConversation validatedConversation = Objects.requireNonNull(
                conversation, "conversation is required");
        cancelPendingWork();
        conversationId.set(validatedConversation.getId());
        view.showConversation(validatedConversation);
    }

    /**
     * Invalidates pending callbacks when the screen is no longer active.
     */
    public void cancelPendingWork() {
        requestVersion.incrementAndGet();
        requestInFlight.set(false);
    }

    private void requestConversation(long version, UUID requestedConversationId, String message) {
        try {
            AssistantConversation response = requestedConversationId == null
                    ? assistantDataSource.startConversation(message)
                    : assistantDataSource.continueConversation(requestedConversationId, message);
            if (response == null
                    || (requestedConversationId != null
                    && !requestedConversationId.equals(response.getId()))) {
                dispatchFailure(version);
                return;
            }
            uiExecutor.execute(() -> renderConversation(version, response));
        } catch (IOException | RuntimeException exception) {
            dispatchFailure(version);
        }
    }

    private void renderConversation(long version, AssistantConversation conversation) {
        if (version != requestVersion.get()) {
            return;
        }
        requestInFlight.set(false);
        conversationId.set(conversation.getId());
        view.showConversation(conversation);
        view.clearComposer();
        view.setComposerEnabled(true);
    }

    private void dispatchFailure(long version) {
        try {
            uiExecutor.execute(() -> renderFailure(version));
        } catch (RuntimeException ignored) {
            // A stopped UI may reject callbacks. There is no user-facing action left to take.
        }
    }

    private void renderFailure(long version) {
        if (version != requestVersion.get()) {
            return;
        }
        requestInFlight.set(false);
        view.showError(GENERIC_REQUEST_ERROR);
        view.setComposerEnabled(true);
    }

    private String validateAndNormalize(String rawMessage) {
        if (rawMessage == null) {
            view.showInputError(EMPTY_MESSAGE_ERROR);
            return null;
        }
        String message = rawMessage.trim();
        if (message.isEmpty()) {
            view.showInputError(EMPTY_MESSAGE_ERROR);
            return null;
        }
        if (message.codePointCount(0, message.length()) > MAX_USER_MESSAGE_LENGTH) {
            view.showInputError(TOO_LONG_MESSAGE_ERROR);
            return null;
        }
        if (containsUnsupportedControlCharacter(message)) {
            view.showInputError(UNSUPPORTED_CHARACTER_ERROR);
            return null;
        }
        return message;
    }

    private static boolean containsUnsupportedControlCharacter(String message) {
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            if (Character.isISOControl(character)
                    && character != '\n'
                    && character != '\r'
                    && character != '\t') {
                return true;
            }
        }
        return false;
    }
}
