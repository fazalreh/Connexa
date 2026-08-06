package com.connexa.mobile.core.auth;

import com.connexa.mobile.core.network.ApiEndpointResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Asks the service who the caller is.
 *
 * <p>Roles are read from here rather than inferred on the device. A client that decided its
 * own permissions would be deciding them from data it could edit; this returns what the
 * server concluded from the verified token, and the server enforces the same conclusion on
 * every request regardless of what the client believes.
 *
 * <p>Used only to decide what to put on screen. Hiding a control the caller cannot use is a
 * courtesy, not a security boundary — the boundary is the server refusing the request.
 */
public final class CurrentIdentityClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final int MAX_RESPONSE_CHARS = 20_000;
    private static final String ORGANIZER = "ORGANIZER";

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;

    public CurrentIdentityClient(
            ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    /**
     * @return true only when the service says so; false for a signed-out caller, a failed
     *     request, or anything unexpected. Guessing true would put a control on screen that
     *     leads to a refusal the person cannot act on.
     */
    public boolean isOrganizer() {
        try {
            URLConnection connection =
                    endpointResolver.currentIdentity().toURL().openConnection();
            if (!(connection instanceof HttpURLConnection http)) {
                return false;
            }
            try {
                http.setRequestMethod("GET");
                http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                http.setReadTimeout(READ_TIMEOUT_MILLIS);
                http.setRequestProperty("Accept", "application/json");
                http.setRequestProperty(
                        "Authorization", "Bearer " + tokenProvider.currentBearerToken());
                if (http.getResponseCode() / 100 != 2) {
                    return false;
                }
                JSONArray roles = new JSONObject(read(http.getInputStream()))
                        .optJSONArray("roles");
                if (roles == null) {
                    return false;
                }
                for (int index = 0; index < roles.length(); index++) {
                    if (ORGANIZER.equalsIgnoreCase(roles.optString(index))) {
                        return true;
                    }
                }
                return false;
            } finally {
                http.disconnect();
            }
        } catch (IOException | JSONException | RuntimeException unavailable) {
            return false;
        }
    }

    private static String read(InputStream stream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
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
