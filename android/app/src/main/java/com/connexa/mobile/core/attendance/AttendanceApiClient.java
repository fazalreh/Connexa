package com.connexa.mobile.core.attendance;

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
import java.util.Objects;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Authenticated HTTP implementation of the attendee saved-event and RSVP contract.
 *
 * <p>The bearer token is obtained only at request time through the configured identity boundary.
 * This client does not embed, persist, log, or forward identity-provider credentials.</p>
 */
public final class AttendanceApiClient implements AttendanceDataSource {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final int MAX_RESPONSE_CHARS = 1_000_000;

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;

    public AttendanceApiClient(ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver is required");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider is required");
    }

    @Override
    public AttendanceState getAttendance(UUID eventId) throws IOException {
        return requestState(eventId, endpointResolver.attendance(requireEventId(eventId)), "GET", null);
    }

    @Override
    public AttendanceState saveEvent(UUID eventId) throws IOException {
        return requestState(eventId, endpointResolver.savedEvent(requireEventId(eventId)), "PUT", null);
    }

    @Override
    public AttendanceState unsaveEvent(UUID eventId) throws IOException {
        return requestState(eventId, endpointResolver.savedEvent(requireEventId(eventId)), "DELETE", null);
    }

    @Override
    public AttendanceState setRsvp(UUID eventId, RsvpStatus status) throws IOException {
        Objects.requireNonNull(status, "status is required");
        JSONObject request = new JSONObject();
        try {
            request.put("status", status.toApiValue());
        } catch (JSONException exception) {
            throw new IOException("Unable to prepare the attendance request.", exception);
        }
        return requestState(eventId, endpointResolver.eventRsvp(requireEventId(eventId)), "PUT", request);
    }

    @Override
    public AttendanceState clearRsvp(UUID eventId) throws IOException {
        return requestState(eventId, endpointResolver.eventRsvp(requireEventId(eventId)), "DELETE", null);
    }

    private AttendanceState requestState(
            UUID expectedEventId,
            URI endpoint,
            String method,
            JSONObject payload) throws IOException {
        AttendanceState state = parseState(requestJson(endpoint, method, payload));
        if (!expectedEventId.equals(state.getEventId())) {
            throw new IOException("The attendance service returned a mismatched event state.");
        }
        return state;
    }

    private JSONObject requestJson(URI endpoint, String method, JSONObject payload) throws IOException {
        String token = requireBearerToken();
        URLConnection rawConnection = endpoint.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection)) {
            throw new IOException("The attendance service endpoint is not HTTP.");
        }

        HttpURLConnection connection = (HttpURLConnection) rawConnection;
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString());

        try {
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        connection.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(payload.toString());
                }
            }
            int statusCode = connection.getResponseCode();
            String body = readBody(statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (statusCode < 200 || statusCode >= 300) {
                throw new AttendanceApiException(statusCode, publicMessage(statusCode));
            }
            try {
                return new JSONObject(body);
            } catch (JSONException exception) {
                throw new IOException("The attendance service returned an unreadable response.", exception);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String requireBearerToken() throws IOException {
        final String suppliedToken;
        try {
            suppliedToken = tokenProvider.currentBearerToken();
        } catch (IOException exception) {
            throw new IOException("Secure account access is unavailable.");
        }
        if (suppliedToken == null) {
            throw new IOException("Secure account access is unavailable.");
        }
        String token = suppliedToken.trim();
        if (token.isEmpty() || containsControlCharacter(token)) {
            throw new IOException("Secure account access is unavailable.");
        }
        return token;
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static AttendanceState parseState(JSONObject source) throws IOException {
        try {
            UUID eventId = UUID.fromString(requireText(source, "eventId"));
            boolean saved = requireBoolean(source, "saved");
            requirePresent(source, "rsvpStatus");
            RsvpStatus rsvpStatus = source.isNull("rsvpStatus")
                    ? null
                    : RsvpStatus.fromApiValue(requireText(source, "rsvpStatus"));
            requirePresent(source, "updatedAt");
            Instant updatedAt = source.isNull("updatedAt")
                    ? null
                    : Instant.parse(requireText(source, "updatedAt"));
            return new AttendanceState(eventId, saved, rsvpStatus, updatedAt);
        } catch (IllegalArgumentException exception) {
            throw new IOException("The attendance service returned an invalid response.", exception);
        }
    }

    private static String requireText(JSONObject source, String field) throws IOException {
        if (!source.has(field) || source.isNull(field)) {
            throw new IOException("The attendance service response is missing a required field.");
        }
        String value = source.optString(field, "").trim();
        if (value.isEmpty()) {
            throw new IOException("The attendance service response contains an empty required field.");
        }
        return value;
    }

    private static boolean requireBoolean(JSONObject source, String field) throws IOException {
        if (!source.has(field) || source.isNull(field) || !(source.opt(field) instanceof Boolean)) {
            throw new IOException("The attendance service response contains an invalid boolean field.");
        }
        return (Boolean) source.opt(field);
    }

    private static void requirePresent(JSONObject source, String field) throws IOException {
        if (!source.has(field)) {
            throw new IOException("The attendance service response is missing a required field.");
        }
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
                    throw new IOException("The attendance service response exceeded the safe size limit.");
                }
                response.append(buffer, 0, read);
            }
            return response.toString();
        }
    }

    private static String publicMessage(int statusCode) {
        if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return "Sign in is required to update your event attendance.";
        }
        if (statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
            return "Your account cannot update this event attendance.";
        }
        if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return "The requested event is no longer available.";
        }
        if (statusCode == HttpURLConnection.HTTP_CONFLICT) {
            return "This event changed before your attendance could be updated.";
        }
        if (statusCode >= 500) {
            return "The attendance service is temporarily unavailable.";
        }
        return "The attendance service could not complete the request.";
    }

    private static UUID requireEventId(UUID eventId) {
        return Objects.requireNonNull(eventId, "eventId is required");
    }
}
