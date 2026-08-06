package com.connexa.mobile.core.organizer;

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
import java.util.Objects;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Creates and publishes organizer events through the Connexa API.
 *
 * <p>The bearer token is fetched per request through the identity boundary. Whether the
 * caller may act as an organizer is decided by the server from that token; this client never
 * asserts a role.
 */
public final class OrganizerApiClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_RESPONSE_CHARS = 200_000;

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;

    public OrganizerApiClient(
            ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    /**
     * Creates the draft and publishes it in one call.
     *
     * <p>Presented as a single action because an organizer who filled in a form expects the
     * event to appear. Leaving a draft behind on a failed publish would be invisible to them.
     *
     * @return the published event's identifier
     */
    public UUID createAndPublish(OrganizerEventDraft draft) throws OrganizerApiException {
        Objects.requireNonNull(draft, "draft is required");
        JSONObject body;
        try {
            body = new JSONObject()
                    .put("title", draft.getTitle())
                    .put("description", draft.getDescription())
                    .put("location", draft.getLocation())
                    .put("startsAt", draft.getStartsAt().toString())
                    .put("endsAt", draft.getEndsAt().toString())
                    .put("timeZone", java.time.ZoneId.systemDefault().getId())
                    .put("category", draft.getCategory())
                    .put("capacity", draft.getCapacity() == null ? 100 : draft.getCapacity());
        } catch (JSONException malformed) {
            throw new OrganizerApiException("Could not prepare the event.", malformed);
        }

        JSONObject created = send(endpointResolver.organizerEvents(), "POST", body.toString());
        String draftId = created.optString("id", null);
        if (draftId == null) {
            throw new OrganizerApiException("The service did not return a draft.");
        }

        JSONObject published;
        try {
            published = send(endpointResolver.publishOrganizerEvent(UUID.fromString(draftId)),
                    "POST", null);
        } catch (IllegalArgumentException malformedId) {
            throw new OrganizerApiException("The service returned an unusable draft.", malformedId);
        }
        String eventId = published.optString("id", null);
        if (eventId == null) {
            throw new OrganizerApiException("The event was not published.");
        }
        return UUID.fromString(eventId);
    }

    private JSONObject send(URI uri, String method, String body) throws OrganizerApiException {
        URLConnection connection;
        try {
            connection = uri.toURL().openConnection();
        } catch (IOException unreachable) {
            throw new OrganizerApiException("Could not reach Connexa.", unreachable);
        }
        if (!(connection instanceof HttpURLConnection http)) {
            throw new OrganizerApiException("Unsupported API endpoint.");
        }
        try {
            http.setRequestMethod(method);
            http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            http.setReadTimeout(READ_TIMEOUT_MILLIS);
            http.setRequestProperty("Accept", "application/json");
            http.setRequestProperty("Authorization", "Bearer " + tokenProvider.currentBearerToken());
            if (body != null) {
                http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                http.setDoOutput(true);
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(http.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(body);
                }
            }
            int status = http.getResponseCode();
            if (status / 100 != 2) {
                throw new OrganizerApiException(messageFor(status));
            }
            return parse(read(http.getInputStream()));
        } catch (IOException failed) {
            throw new OrganizerApiException("Could not reach Connexa.", failed);
        } finally {
            http.disconnect();
        }
    }

    /** Server wording is not shown verbatim; these are written for an organizer. */
    private static String messageFor(int status) {
        return switch (status) {
            case 401 -> "Sign in to publish an event.";
            case 403 -> "Your account is not set up to publish events.";
            case 400, 422 -> "Some details were rejected. Check the dates and try again.";
            case 503 -> "Publishing is unavailable right now. Try again shortly.";
            default -> "Could not publish the event (" + status + ").";
        };
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

    private static JSONObject parse(String payload) throws OrganizerApiException {
        try {
            return new JSONObject(payload);
        } catch (JSONException malformed) {
            throw new OrganizerApiException("The service returned an unexpected response.", malformed);
        }
    }
}
