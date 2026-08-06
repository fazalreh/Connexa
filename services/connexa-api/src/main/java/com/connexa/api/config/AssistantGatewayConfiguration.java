package com.connexa.api.config;

import com.connexa.api.infrastructure.assistant.AssistantGateway;
import com.connexa.api.infrastructure.assistant.AssistantRateLimiter;
import com.connexa.api.infrastructure.assistant.GeminiTextModel;
import com.connexa.api.infrastructure.assistant.GroundedAssistantGateway;
import com.connexa.api.infrastructure.assistant.UnavailableAssistantGateway;
import com.connexa.api.infrastructure.event.EventCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the assistant boundary. Exactly one {@link AssistantGateway} exists: the
 * fail-safe default, or a provider-backed one chosen by an explicit mode.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "connexa.assistant.mode",
            havingValue = "disabled",
            matchIfMissing = true)
    AssistantGateway unavailableAssistantGateway() {
        return new UnavailableAssistantGateway();
    }

    @Bean
    @ConditionalOnProperty(name = "connexa.assistant.mode", havingValue = "gemini")
    AssistantGateway groundedAssistantGateway(
            EventCatalog eventCatalog, AssistantProperties properties) {
        // A private mapper rather than the web layer's: this one only ever handles the
        // provider's request and response shapes, so it should not inherit or influence
        // however the API chooses to serialise its own payloads.
        ObjectMapper objectMapper = new ObjectMapper();
        String model = require(properties.model(), "connexa.assistant.model");
        String apiKey = require(properties.apiKey(), "connexa.assistant.api-key");
        Clock clock = Clock.systemUTC();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        return new GroundedAssistantGateway(
                eventCatalog,
                new GeminiTextModel(
                        httpClient,
                        objectMapper,
                        model,
                        apiKey,
                        properties.maxOutputTokens(),
                        properties.requestTimeout()),
                new AssistantRateLimiter(properties.maxRequestsPerMinute(), clock),
                properties.maxContextEvents(),
                clock);
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " must be set when connexa.assistant.mode=gemini");
        }
        return value;
    }
}
