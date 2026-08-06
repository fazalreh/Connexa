package com.connexa.mobile.core.checkin;

import com.connexa.mobile.core.auth.IdentityTokenProvider;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
 * Issues door passes and redeems scanned ones.
 *
 * <p>Both sides of the door talk to the same service. The attendee asks for a pass for
 * themselves; the steward presents one they scanned. Neither can do the other's job: the
 * server takes the attendee's identity from the token rather than from the request, and
 * refuses to redeem for anyone without the organizer role.
 */
public final class CheckInApiClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final int MAX_RESPONSE_CHARS = 100_000;

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;

    public CheckInApiClient(
            ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    /** Asks for a fresh pass for the signed-in attendee. */
    public IssuedPass issuePass(UUID eventId) throws IOException {
        JSONObject payload = request(endpointResolver.checkInPass(eventId), "GET", null);
        if (payload == null) {
            throw new CheckInException("This event is not accepting check-in.");
        }
        return new IssuedPass(
                payload.optString("pass", ""),
                Instant.parse(payload.optString("expiresAt")));
    }

    /** Redeems a scanned pass. Organizer only; the server enforces that, not this client. */
    public CheckInOutcome redeem(UUID eventId, String scanned) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("pass", scanned == null ? "" : scanned.trim());
        } catch (JSONException impossible) {
            throw new IOException("Could not build the request.", impossible);
        }
        JSONObject payload = request(endpointResolver.checkIn(eventId), "POST", body.toString());
        if (payload == null) {
            throw new CheckInException("The door did not recognise that code.");
        }
        return new CheckInOutcome(
                Instant.parse(payload.optString("arrivedAt")),
                payload.optBoolean("alreadyCheckedIn", false));
    }

    private JSONObject request(URI uri, String method, String body) throws IOException {
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
            if (body != null) {
                http.setDoOutput(true);
                http.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = http.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = http.getResponseCode();
            // A refused pass is the ordinary outcome of a bad scan, not a fault. The
            // message is written to be read out at the door rather than logged.
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new CheckInException("Sign in again to use check-in.");
            }
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new CheckInException("Only an organizer can check people in.");
            }
            if (status == HttpURLConnection.HTTP_BAD_REQUEST
                    || status == HttpURLConnection.HTTP_CONFLICT
                    || status == 422) {
                throw new CheckInException(detailOf(http) == null
                        ? "That pass is not valid for this event."
                        : detailOf(http));
            }
            if (status == HttpURLConnection.HTTP_NOT_FOUND
                    || status == HttpURLConnection.HTTP_NO_CONTENT) {
                return null;
            }
            if (status / 100 != 2) {
                throw new IOException("Check-in is unavailable (" + status + ").");
            }
            String payload = read(http.getInputStream());
            if (payload.isBlank()) {
                return null;
            }
            try {
                return new JSONObject(payload);
            } catch (JSONException malformed) {
                throw new IOException("Unreadable response.", malformed);
            }
        } finally {
            http.disconnect();
        }
    }

    /** The server explains a refusal in the problem body; that wording is better than ours. */
    private static String detailOf(HttpURLConnection http) {
        try (InputStream errors = http.getErrorStream()) {
            if (errors == null) {
                return null;
            }
            String body = read(errors);
            String detail = new JSONObject(body).optString("detail", "");
            return detail.isBlank() ? null : detail;
        } catch (IOException | JSONException | RuntimeException unreadable) {
            return null;
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
