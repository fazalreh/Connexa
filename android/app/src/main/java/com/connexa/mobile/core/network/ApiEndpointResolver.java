package com.connexa.mobile.core.network;

import java.net.URI;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.UUID;

public final class ApiEndpointResolver {

    private final URI baseUri;

    public ApiEndpointResolver(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("API base URL is required");
        }
        URI parsed = URI.create(baseUrl);
        if (parsed.getScheme() == null || parsed.getHost() == null) {
            throw new IllegalArgumentException("API base URL must be absolute");
        }
        this.baseUri = URI.create(ensureTrailingSlash(parsed.toString()));
    }

    public URI platformStatus() {
        return baseUri.resolve("api/v1/platform/status");
    }

    public URI events(String query, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > 120) {
            throw new IllegalArgumentException("query must not exceed 120 characters");
        }

        String path = "api/v1/events?page=" + page + "&size=" + size;
        if (!normalizedQuery.isEmpty()) {
            path += "&q=" + encodeQueryValue(normalizedQuery);
        }
        return baseUri.resolve(path);
    }

    public URI event(UUID eventId) {
        return baseUri.resolve("api/v1/events/" + Objects.requireNonNull(eventId, "eventId is required"));
    }

    /** Returns the authenticated saved-event and RSVP state for one event. */
    public URI attendance(UUID eventId) {
        return eventSubresource(eventId, "attendance");
    }

    /** Returns the authenticated saved-event mutation endpoint for one event. */
    public URI savedEvent(UUID eventId) {
        return eventSubresource(eventId, "saved");
    }

    /** Returns the authenticated RSVP mutation endpoint for one event. */
    public URI eventRsvp(UUID eventId) {
        return eventSubresource(eventId, "rsvp");
    }

    public URI assistantMessages() {
        return baseUri.resolve("api/v1/assistant/messages");
    }

    private URI eventSubresource(UUID eventId, String subresource) {
        return baseUri.resolve(
                "api/v1/events/"
                        + Objects.requireNonNull(eventId, "eventId is required")
                        + "/"
                        + subresource);
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private static String encodeQueryValue(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 must be available on every Android runtime", exception);
        }
    }
}
