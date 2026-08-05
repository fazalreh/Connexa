package com.connexa.mobile.core.network;

import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventPage;
import com.connexa.mobile.core.events.EventSummary;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * HTTP implementation of the public, read-only event API.
 *
 * <p>This client deliberately contains no credentials, vendor SDKs, or write operations.</p>
 */
public final class EventApiClient implements EventDataSource {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_RESPONSE_CHARS = 1_000_000;

    private final ApiEndpointResolver endpointResolver;

    public EventApiClient(ApiEndpointResolver endpointResolver) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver is required");
    }

    @Override
    public EventPage listPublishedEvents(String query) throws IOException {
        return listPublishedEvents(query, 0, DEFAULT_PAGE_SIZE);
    }

    @Override
    public EventPage listPublishedEvents(String query, int page, int size) throws IOException {
        return parsePage(requestJson(endpointResolver.events(query, page, size)));
    }

    @Override
    public EventSummary getEvent(UUID eventId) throws IOException {
        return parseEvent(requestJson(endpointResolver.event(eventId)));
    }

    private JSONObject requestJson(URI endpoint) throws IOException {
        URLConnection rawConnection = endpoint.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection)) {
            throw new IOException("The event service endpoint is not HTTP.");
        }

        HttpURLConnection connection = (HttpURLConnection) rawConnection;
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString());

        try {
            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readBody(responseStream);
            if (statusCode < 200 || statusCode >= 300) {
                throw new EventApiException(statusCode, publicMessage(statusCode));
            }
            try {
                return new JSONObject(body);
            } catch (JSONException exception) {
                throw new IOException("The event service returned an unreadable response.", exception);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static EventPage parsePage(JSONObject source) throws IOException {
        JSONArray items = source.optJSONArray("items");
        if (items == null) {
            throw new IOException("The event service response did not contain an event list.");
        }

        List<EventSummary> events = new ArrayList<>(items.length());
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                throw new IOException("The event service returned an invalid event.");
            }
            events.add(parseEvent(item));
        }

        try {
            return new EventPage(
                    events,
                    requireNonNegativeInt(source, "page"),
                    requirePageSize(source, "size"),
                    requireNonNegativeLong(source, "total"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("The event service returned an invalid page.", exception);
        }
    }

    private static EventSummary parseEvent(JSONObject source) throws IOException {
        try {
            return new EventSummary(
                    UUID.fromString(requireText(source, "id")),
                    requireText(source, "title"),
                    requireText(source, "summary"),
                    Instant.parse(requireText(source, "startsAt")),
                    Instant.parse(requireText(source, "endsAt")),
                    requireText(source, "timeZone"),
                    requireText(source, "venueName"),
                    requireText(source, "category"),
                    requireText(source, "organizerName"),
                    requireText(source, "status"),
                    requireNonNegativeLong(source, "revision"),
                    Instant.parse(requireText(source, "createdAt")),
                    Instant.parse(requireText(source, "updatedAt")));
        } catch (IllegalArgumentException exception) {
            throw new IOException("The event service returned an invalid event.", exception);
        }
    }

    private static String requireText(JSONObject source, String field) throws IOException {
        if (!source.has(field) || source.isNull(field)) {
            throw new IOException("The event service response is missing a required field.");
        }
        String value = source.optString(field, "").trim();
        if (value.isEmpty()) {
            throw new IOException("The event service response contains an empty required field.");
        }
        return value;
    }

    private static int requireNonNegativeInt(JSONObject source, String field) throws IOException {
        long value = requireNonNegativeLong(source, field);
        if (value > Integer.MAX_VALUE) {
            throw new IOException("The event service response contains an out-of-range value.");
        }
        return (int) value;
    }

    private static int requirePageSize(JSONObject source, String field) throws IOException {
        int value = requireNonNegativeInt(source, field);
        if (value < 1 || value > 100) {
            throw new IOException("The event service response contains an invalid page size.");
        }
        return value;
    }

    private static long requireNonNegativeLong(JSONObject source, String field) throws IOException {
        if (!source.has(field) || source.isNull(field)) {
            throw new IOException("The event service response is missing a required field.");
        }
        long value = source.optLong(field, Long.MIN_VALUE);
        if (value < 0) {
            throw new IOException("The event service response contains an invalid numeric value.");
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
                    throw new IOException("The event service response exceeded the safe size limit.");
                }
                response.append(buffer, 0, read);
            }
            return response.toString();
        }
    }

    private static String publicMessage(int statusCode) {
        if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return "The requested event is no longer available.";
        }
        if (statusCode >= 500) {
            return "The event service is temporarily unavailable.";
        }
        return "The event service could not complete the request.";
    }
}
