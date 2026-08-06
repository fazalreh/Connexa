package com.connexa.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "connexa")
public record ConnexaProperties(
        @NotBlank String serviceName,
        @NotBlank String environment) {

}
