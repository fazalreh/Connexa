package com.connexa.api.infrastructure.assistant;

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
 * Minimal HTTP client for the provider's text-generation endpoint.
 *
 * <p>Written against the JDK HTTP client rather than a provider SDK: the surface used here is
 * one request shape, and a direct call keeps the credential and the timeout under this
 * class's control.
 *
 * <p>The API key is passed as a header rather than a query parameter so it cannot end up in
 * an access log or a proxy trace.
 */
public final class GeminiTextModel {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;
    private final int maxOutputTokens;
    private final Duration timeout;

    public GeminiTextModel(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String model,
            String apiKey,
            int maxOutputTokens,
            Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.model = Objects.requireNonNull(model, "model is required");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey is required");
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = Objects.requireNonNull(timeout, "timeout is required");
    }

    /**
     * @return the model's text, or empty when the provider returned no usable candidate
     * @throws IOException on transport failure or a non-success status
     */
    public String generate(String systemInstruction, String userContent)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT.formatted(model)))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        body(systemInstruction, userContent), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            // The body can echo request content; keep it out of the exception message.
            throw new IOException("Assistant provider returned status " + response.statusCode());
        }
        return firstCandidateText(objectMapper.readTree(response.body()));
    }

    private String body(String systemInstruction, String userContent) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts")
                .addObject().put("text", systemInstruction);
        root.putArray("contents").addObject()
                .put("role", "user")
                .putArray("parts").addObject().put("text", userContent);
        ObjectNode generation = root.putObject("generationConfig");
        generation.put("maxOutputTokens", maxOutputTokens);
        // Low temperature: this assistant restates retrieved facts, so variability is a defect.
        generation.put("temperature", 0.2);
        return root.toString();
    }

    private static String firstCandidateText(JsonNode payload) {
        JsonNode parts = payload.path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            String value = part.path("text").asText("");
            if (!value.isBlank()) {
                text.append(value);
            }
        }
        return text.toString().trim();
    }
}
