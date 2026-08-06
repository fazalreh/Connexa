package com.connexa.api.infrastructure.search;

import com.connexa.api.domain.search.EmbeddingVector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Embeds text with the provider's embedding endpoint.
 *
 * <p>Documents and queries are embedded with different task types. Providers train these
 * asymmetrically — a query is a short question, a document is a description — and using one
 * setting for both measurably degrades the ranking.
 */
public final class GeminiEmbeddingModel implements EmbeddingModel {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent";
    private static final String DOCUMENT_TASK = "RETRIEVAL_DOCUMENT";
    private static final String QUERY_TASK = "RETRIEVAL_QUERY";
    /** Truncated so one unusually long description cannot dominate a request. */
    private static final int MAX_INPUT_CHARS = 8_000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;
    private final int dimensions;
    private final Duration timeout;

    public GeminiEmbeddingModel(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String model,
            String apiKey,
            int dimensions,
            Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.model = Objects.requireNonNull(model, "model is required");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey is required");
        this.dimensions = dimensions;
        this.timeout = Objects.requireNonNull(timeout, "timeout is required");
    }

    @Override
    public String name() {
        return model;
    }

    @Override
    public EmbeddingVector embedDocument(String text) {
        return embed(text, DOCUMENT_TASK);
    }

    @Override
    public EmbeddingVector embedQuery(String text) {
        return embed(text, QUERY_TASK);
    }

    private EmbeddingVector embed(String text, String taskType) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text is required");
        }
        String trimmed = text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", "models/" + model);
        root.put("taskType", taskType);
        root.put("outputDimensionality", dimensions);
        root.putObject("content").putArray("parts").addObject().put("text", trimmed);

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT.formatted(model)))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                // Header rather than query parameter so the key cannot reach an access log.
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new EmbeddingUnavailableException(
                        "Embedding provider returned status " + response.statusCode());
            }
            return parse(objectMapper.readTree(response.body()));
        } catch (IOException exception) {
            throw new EmbeddingUnavailableException("Embedding provider could not be reached", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingUnavailableException("Embedding request was interrupted", exception);
        }
    }

    private static EmbeddingVector parse(JsonNode payload) {
        JsonNode values = payload.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new EmbeddingUnavailableException("Embedding provider returned no vector");
        }
        float[] components = new float[values.size()];
        for (int index = 0; index < components.length; index++) {
            components[index] = (float) values.get(index).asDouble();
        }
        // Normalised on construction, so a provider that returns un-normalised vectors
        // cannot skew ranking by magnitude.
        return EmbeddingVector.of(components);
    }
}
