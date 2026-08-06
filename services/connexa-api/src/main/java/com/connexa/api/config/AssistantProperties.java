package com.connexa.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the server-side assistant provider.
 *
 * <p>Bound only when an explicit provider mode is selected. The disabled default needs none
 * of it, and in particular holds no credential.
 */
@ConfigurationProperties(prefix = "connexa.assistant")
public record AssistantProperties(
        String provider,
        String model,
        String embeddingModel,
        String apiKey,
        int maxRequestsPerMinute,
        int maxOutputTokens,
        int maxContextEvents,
        Duration requestTimeout) {

    public AssistantProperties {
        // Text generation and embedding are separate models on the same key.
        embeddingModel = embeddingModel == null || embeddingModel.isBlank()
                ? "gemini-embedding-001"
                : embeddingModel;
        maxRequestsPerMinute = maxRequestsPerMinute > 0 ? maxRequestsPerMinute : 20;
        maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : 1_024;
        maxContextEvents = maxContextEvents > 0 ? maxContextEvents : 8;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
    }
}
