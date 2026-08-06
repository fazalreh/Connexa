package com.connexa.mobile.core.attendance;

import com.connexa.mobile.core.auth.IdentityTokenProvider;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Watches an event's seat count and reports every change.
 *
 * <p>A server-sent event stream is a long-lived read: the connection stays open and the
 * server writes when something happens. That makes cancellation the important part — a
 * blocking read does not return on its own, so the socket is closed to unblock it when the
 * screen goes away.
 *
 * <p>The stream carries comment lines as keep-alives. They are skipped rather than parsed.
 */
public final class CapacityStream {

    /** Delivered on the reading thread; callers marshal to their own. */
    public interface Listener {
        void onCapacity(EventCapacity capacity);
    }

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    /** No read timeout: silence is the normal state of an idle stream. */
    private static final int READ_TIMEOUT_MILLIS = 0;
    private static final String DATA_PREFIX = "data:";

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile HttpURLConnection connection;

    public CapacityStream(
            ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    /** Blocks until the stream ends or {@link #cancel()} is called. */
    public void watch(UUID eventId, Listener listener) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(listener, "listener is required");
        try {
            URLConnection raw = endpointResolver.capacityStream(eventId).toURL().openConnection();
            if (!(raw instanceof HttpURLConnection http)) {
                return;
            }
            connection = http;
            http.setRequestMethod("GET");
            http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            http.setReadTimeout(READ_TIMEOUT_MILLIS);
            http.setRequestProperty("Accept", "text/event-stream");
            http.setRequestProperty("Authorization", "Bearer " + tokenProvider.currentBearerToken());

            if (http.getResponseCode() / 100 != 2) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(http.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    if (!line.startsWith(DATA_PREFIX)) {
                        continue;
                    }
                    EventCapacity capacity = parse(eventId, line.substring(DATA_PREFIX.length()).trim());
                    if (capacity != null) {
                        listener.onCapacity(capacity);
                    }
                }
            }
        } catch (IOException | RuntimeException ended) {
            // Covers a genuine disconnect and the socket close used to cancel. Neither is
            // worth reporting: the screen either went away or will reconnect on return.
        } finally {
            close();
        }
    }

    /** Unblocks the reader by closing the socket underneath it. */
    public void cancel() {
        cancelled.set(true);
        close();
    }

    private void close() {
        HttpURLConnection open = connection;
        connection = null;
        if (open != null) {
            open.disconnect();
        }
    }

    private static EventCapacity parse(UUID eventId, String payload) {
        try {
            JSONObject json = new JSONObject(payload);
            return new EventCapacity(
                    eventId,
                    json.isNull("totalCapacity") ? null : json.optInt("totalCapacity"),
                    json.isNull("spotsRemaining") ? null : json.optInt("spotsRemaining"),
                    json.optInt("reserved", 0),
                    json.optBoolean("full", false));
        } catch (JSONException malformed) {
            // One unreadable frame must not end the stream.
            return null;
        }
    }
}
