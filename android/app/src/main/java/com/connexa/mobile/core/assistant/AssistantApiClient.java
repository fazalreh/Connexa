package com.connexa.mobile.core.assistant;

import com.connexa.mobile.core.auth.IdentityTokenProvider;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Authenticated HTTP implementation of the server-side event assistant contract.
 *
 * <p>Only the Connexa API is contacted. The mobile app never sends a model-provider key or calls
 * a model-provider endpoint directly.</p>
 */
public final class AssistantApiClient implements AssistantDataSource {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private static final int MAX_USER_MESSAGE_LENGTH = 1_000;

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;
    private final Map<UUID, ConversationState> conversations = new HashMap<>();

    public AssistantApiClient(ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver is required");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider is required");
    }

    @Override
    public synchronized AssistantConversation startConversation(String message) throws IOException {
        return send(null, message);
    }

    @Override
    public synchronized AssistantConversation continueConversation(UUID conversationId, String message)
            throws IOException {
        return send(Objects.requireNonNull(conversationId, "conversationId is required"), message);
    }

    private AssistantConversation send(UUID conversationId, String message) throws IOException {
        String normalizedMessage = requireUserMessage(message);
        JSONObject request = new JSONObject();
        try {
            request.put("message", normalizedMessage);
            if (conversationId != null) {
                request.put("conversationId", conversationId.toString());
            }
        } catch (JSONException exception) {
            throw new IOException("Unable to prepare the assistant request.", exception);
        }

        AssistantReply reply = parseReply(postJson(endpointResolver.assistantMessages(), request));
        if (conversationId != null && !conversationId.equals(reply.conversationId)) {
            throw new IOException("The assistant response did not match this conversation.");
        }

        ConversationState existing = conversations.get(reply.conversationId);
        if (conversationId != null && existing == null) {
            throw new IOException("This assistant conversation is no longer available on this device.");
        }

        Instant timestamp = reply.createdAt;
        List<AssistantMessage> messages = existing == null
                ? new ArrayList<>()
                : new ArrayList<>(existing.messages);
        Instant createdAt = existing == null ? timestamp : existing.createdAt;
        if (timestamp.isBefore(createdAt)) {
            throw new IOException("The assistant response contained an invalid timestamp.");
        }
        if (messages.size() + 2 > AssistantConversation.MAX_MESSAGE_COUNT) {
            throw new IOException("This assistant conversation has reached its safe message limit.");
        }
        messages.add(new AssistantMessage(
                UUID.randomUUID(),
                reply.conversationId,
                AssistantMessageRole.USER,
                normalizedMessage,
                Collections.emptyList(),
                timestamp));
        messages.add(new AssistantMessage(
                UUID.randomUUID(),
                reply.conversationId,
                AssistantMessageRole.ASSISTANT,
                reply.message,
                reply.references,
                timestamp));

        AssistantConversation conversation = new AssistantConversation(
                reply.conversationId,
                createdAt,
                timestamp,
                messages);
        conversations.put(reply.conversationId, new ConversationState(createdAt, messages));
        return conversation;
    }

    private JSONObject postJson(URI endpoint, JSONObject payload) throws IOException {
        URLConnection rawConnection = endpoint.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection)) {
            throw new IOException("The assistant service endpoint is not HTTP.");
        }
        String token = tokenProvider.currentBearerToken();
        if (token == null || token.trim().isEmpty() || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new IOException("Secure account access is unavailable.");
        }

        HttpURLConnection connection = (HttpURLConnection) rawConnection;
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString());

        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    connection.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(payload.toString());
            }
            int statusCode = connection.getResponseCode();
            String body = readBody(statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException(publicMessage(statusCode));
            }
            try {
                return new JSONObject(body);
            } catch (JSONException exception) {
                throw new IOException("The assistant service returned an unreadable response.", exception);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static AssistantReply parseReply(JSONObject source) throws IOException {
        try {
            UUID conversationId = UUID.fromString(requireText(source, "conversationId"));
            String message = requireText(source, "message");
            if (message.length() > AssistantMessage.MAX_CONTENT_LENGTH) {
                throw new IOException("The assistant service response exceeds the supported length.");
            }
            Instant createdAt = Instant.parse(requireText(source, "createdAt"));
            JSONArray sourceReferences = source.optJSONArray("references");
            if (sourceReferences == null) {
                throw new IOException("The assistant service response is missing event references.");
            }
            List<AssistantReference> references = new ArrayList<>(sourceReferences.length());
            for (int index = 0; index < sourceReferences.length(); index++) {
                JSONObject reference = sourceReferences.optJSONObject(index);
                if (reference == null) {
                    throw new IOException("The assistant service returned an invalid event reference.");
                }
                references.add(new AssistantReference(
                        UUID.fromString(requireText(reference, "eventId")),
                        requireText(reference, "label"),
                        ""));
            }
            return new AssistantReply(conversationId, message, references, createdAt);
        } catch (IllegalArgumentException exception) {
            throw new IOException("The assistant service returned an invalid response.", exception);
        }
    }

    private static String requireUserMessage(String message) throws IOException {
        if (message == null) {
            throw new IOException("A message is required.");
        }
        String normalized = message.trim();
        if (normalized.isEmpty()
                || normalized.length() > MAX_USER_MESSAGE_LENGTH
                || containsUnsupportedControlCharacter(normalized)) {
            throw new IOException("The message is invalid.");
        }
        return normalized;
    }

    private static boolean containsUnsupportedControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)
                    && character != '\n'
                    && character != '\r'
                    && character != '\t') {
                return true;
            }
        }
        return false;
    }

    private static String requireText(JSONObject source, String field) throws IOException {
        if (!source.has(field) || source.isNull(field)) {
            throw new IOException("The assistant service response is missing a required field.");
        }
        String value = source.optString(field, "").trim();
        if (value.isEmpty()) {
            throw new IOException("The assistant service response contains an empty required field.");
        }
        return value;
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder response = new StringBuilder();
            char[] buffer = new char[4_096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (response.length() + read > MAX_RESPONSE_CHARS) {
                    throw new IOException("The assistant service response exceeded the safe size limit.");
                }
                response.append(buffer, 0, read);
            }
            return response.toString();
        }
    }

    private static String publicMessage(int statusCode) {
        if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return "Sign in is required to use the event assistant.";
        }
        if (statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
            return "Your account cannot use the event assistant.";
        }
        if (statusCode == HttpURLConnection.HTTP_UNAVAILABLE) {
            return "The event assistant is temporarily unavailable.";
        }
        return "The event assistant could not complete the request.";
    }

    private static final class AssistantReply {

        private final UUID conversationId;
        private final String message;
        private final List<AssistantReference> references;
        private final Instant createdAt;

        AssistantReply(
                UUID conversationId,
                String message,
                List<AssistantReference> references,
                Instant createdAt) {
            this.conversationId = conversationId;
            this.message = message;
            this.references = Collections.unmodifiableList(new ArrayList<>(references));
            this.createdAt = createdAt;
        }
    }

    private static final class ConversationState {

        private final Instant createdAt;
        private final List<AssistantMessage> messages;

        ConversationState(Instant createdAt, List<AssistantMessage> messages) {
            this.createdAt = createdAt;
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        }
    }
}
