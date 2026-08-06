package com.connexa.api.config;

import com.connexa.api.infrastructure.search.EmbeddingModel;
import com.connexa.api.infrastructure.search.EmbeddingStore;
import com.connexa.api.infrastructure.search.GeminiEmbeddingModel;
import com.connexa.api.infrastructure.search.UnavailableEmbeddingStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires semantic search.
 *
 * <p>The model bean exists in every mode so the services can be constructed, but without a
 * configured provider it refuses to embed. Search then finds an empty index and returns
 * nothing, which degrades to keyword discovery rather than failing a request.
 */
@Configuration(proxyBeanMethods = false)
public class SearchConfiguration {

    /** 768 balances quality against 3 KB per stored event. */
    private static final int DIMENSIONS = 768;

    @Bean
    @ConditionalOnMissingBean(EmbeddingStore.class)
    EmbeddingStore unavailableEmbeddingStore() {
        return new UnavailableEmbeddingStore();
    }

    @Bean
    @ConditionalOnProperty(name = "connexa.assistant.mode", havingValue = "gemini")
    EmbeddingModel geminiEmbeddingModel(AssistantProperties properties) {
        return new GeminiEmbeddingModel(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                new ObjectMapper(),
                properties.embeddingModel(),
                properties.apiKey(),
                DIMENSIONS,
                Duration.ofSeconds(30));
    }

    /**
     * Fail-closed default. Present so the application still starts with search disabled;
     * every call reports the provider as unavailable rather than returning a vector that
     * would silently rank badly.
     */
    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    EmbeddingModel unavailableEmbeddingModel() {
        return new com.connexa.api.infrastructure.search.UnavailableEmbeddingModel();
    }
}
