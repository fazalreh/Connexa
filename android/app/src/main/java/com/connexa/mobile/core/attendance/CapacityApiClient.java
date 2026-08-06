package com.connexa.mobile.core.attendance;

import com.connexa.mobile.core.auth.IdentityTokenProvider;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads seat availability and manages the caller's place in an event's queue.
 *
 * <p>Queueing is only offered once an event is full; the screen decides that from the
 * capacity it reads here, and the server refuses a premature request regardless.
 */
public final class CapacityApiClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final int MAX_RESPONSE_CHARS = 100_000;

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;

    public CapacityApiClient(
            ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    public EventCapacity getCapacity(UUID eventId) throws IOException {
        JSONObject payload = request(endpointResolver.capacity(eventId), "GET");
        if (payload == null) {
            throw new IOException("Seat availability is unavailable.");
        }
        return new EventCapacity(
                eventId,
                payload.isNull("totalCapacity") ? null : payload.optInt("totalCapacity"),
                payload.isNull("spotsRemaining") ? null : payload.optInt("spotsRemaining"),
                payload.optInt("reserved", 0),
                payload.optBoolean("full", false));
    }

    /** The caller's own place, or {@link WaitlistPlace#notWaiting()} when they hold none. */
    public WaitlistPlace getWaitlistPlace(UUID eventId) throws IOException {
        JSONObject payload = request(endpointResolver.waitlist(eventId), "GET");
        if (payload == null) {
            // A 404 here means "not queued", which is an ordinary state rather than a fault.
            return WaitlistPlace.notWaiting();
        }
        return new WaitlistPlace(
                payload.isNull("placeInQueue") ? null : payload.optLong("placeInQueue"), true);
    }

    public WaitlistPlace joinWaitlist(UUID eventId) throws IOException {
        JSONObject payload = request(endpointResolver.waitlist(eventId), "POST");
        if (payload == null) {
            return WaitlistPlace.notWaiting();
        }
        return new WaitlistPlace(
                payload.isNull("placeInQueue") ? null : payload.optLong("placeInQueue"), true);
    }

    public void leaveWaitlist(UUID eventId) throws IOException {
        request(endpointResolver.waitlist(eventId), "DELETE");
    }

    /** @return the parsed body, or null when the server had nothing to say (204 or 404) */
    private JSONObject request(URI uri, String method) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        if (!(connection instanceof HttpURLConnection http)) {
            throw new IOException("Unsupported API endpoint.");
        }
        try {
            http.setRequestMethod(method);
            http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            http.setReadTimeout(READ_TIMEOUT_MILLIS);
            http.setRequestProperty("Accept", "application/json");
            http.setRequestProperty("Authorization", "Bearer " + tokenProvider.currentBearerToken());

            int status = http.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND
                    || status == HttpURLConnection.HTTP_NO_CONTENT) {
                return null;
            }
            if (status == HttpURLConnection.HTTP_CONFLICT) {
                throw new IOException("This event still has seats.");
            }
            if (status / 100 != 2) {
                throw new IOException("Could not read seat availability (" + status + ").");
            }
            String body = read(http.getInputStream());
            if (body.isBlank()) {
                return null;
            }
            try {
                return new JSONObject(body);
            } catch (JSONException malformed) {
                throw new IOException("Unreadable response.", malformed);
            }
        } finally {
            http.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                text.append(buffer, 0, count);
                if (text.length() > MAX_RESPONSE_CHARS) {
                    throw new IOException("Response was larger than expected.");
                }
            }
        }
        return text.toString();
    }
}
