package com.connexa.mobile.core.network;

import java.net.URI;
import java.util.Objects;

public final class ApiEndpointResolver {

    private final URI baseUri;

    public ApiEndpointResolver(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
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

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }
}
