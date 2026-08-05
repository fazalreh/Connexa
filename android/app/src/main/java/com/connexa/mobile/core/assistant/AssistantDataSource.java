package com.connexa.mobile.core.assistant;

import java.io.IOException;
import java.util.UUID;

/**
 * Backend-only boundary for event-assistant conversations.
 *
 * <p>Implementations communicate with the Connexa service and must not make direct external-model
 * connections from the Android device.</p>
 */
public interface AssistantDataSource {

    AssistantConversation startConversation(String message) throws IOException;

    AssistantConversation continueConversation(UUID conversationId, String message) throws IOException;
}
