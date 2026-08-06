package com.connexa.mobile.core.push;

import com.connexa.mobile.core.auth.IdentityTokenProvider;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Registers this device with the Connexa API so it can receive notifications.
 *
 * <p>The device is bound server-side to whoever the bearer token identifies. The app never
 * names an account in the request, so a token cannot be attached to someone else.
 */
public final class PushRegistrationClient {

    private static final int CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int READ_TIMEOUT_MILLIS = 8_000;
    private static final String PLATFORM = "android";

    private final ApiEndpointResolver endpointResolver;
    private final IdentityTokenProvider tokenProvider;

    public PushRegistrationClient(
            ApiEndpointResolver endpointResolver, IdentityTokenProvider tokenProvider) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
    }

    public void register(String deviceToken) throws IOException {
        send("POST", deviceToken);
    }

    /** Called on sign-out so a shared device stops receiving the previous user's alerts. */
    public void unregister(String deviceToken) throws IOException {
        send("DELETE", deviceToken);
    }

    private void send(String method, String deviceToken) throws IOException {
        if (deviceToken == null || deviceToken.trim().isEmpty()) {
            throw new IOException("A device token is required.");
        }
        String body;
        try {
            body = new JSONObject()
                    .put("token", deviceToken.trim())
                    .put("platform", PLATFORM)
                    .toString();
        } catch (JSONException malformed) {
            throw new IOException("Could not build the registration request.", malformed);
        }

        URLConnection connection = endpointResolver.deviceRegistration().toURL().openConnection();
        if (!(connection instanceof HttpURLConnection http)) {
            throw new IOException("Unsupported API endpoint.");
        }
        try {
            http.setRequestMethod(method);
            http.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            http.setReadTimeout(READ_TIMEOUT_MILLIS);
            http.setRequestProperty("Authorization", "Bearer " + tokenProvider.currentBearerToken());
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            http.setDoOutput(true);
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(http.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(body);
            }
            int status = http.getResponseCode();
            if (status / 100 != 2) {
                throw new IOException("Device registration failed with status " + status);
            }
        } finally {
            http.disconnect();
        }
    }
}
