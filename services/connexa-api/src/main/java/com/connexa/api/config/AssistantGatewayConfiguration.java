package com.connexa.api.config;

import com.connexa.api.infrastructure.assistant.AssistantGateway;
import com.connexa.api.infrastructure.assistant.UnavailableAssistantGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a fail-safe assistant boundary without embedding a model credential.
 */
@Configuration(proxyBeanMethods = false)
public class AssistantGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "connexa.assistant.mode",
            havingValue = "disabled",
            matchIfMissing = true)
    AssistantGateway unavailableAssistantGateway() {
        return new UnavailableAssistantGateway();
    }
}
