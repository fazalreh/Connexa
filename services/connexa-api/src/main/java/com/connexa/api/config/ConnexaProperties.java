package com.connexa.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "connexa")
public record ConnexaProperties(
        @NotBlank String serviceName,
        @NotBlank String environment,
        @NotNull @Valid Integrations integrations) {

    public record Integrations(
            boolean firebaseEnabled,
            boolean aiEnabled,
            boolean mailEnabled,
            boolean notificationsEnabled) {

        public List<String> disabledNames() {
            List<String> disabled = new ArrayList<>();
            if (!firebaseEnabled) {
                disabled.add("firebase");
            }
            if (!aiEnabled) {
                disabled.add("ai");
            }
            if (!mailEnabled) {
                disabled.add("mail");
            }
            if (!notificationsEnabled) {
                disabled.add("notifications");
            }
            return List.copyOf(disabled);
        }
    }
}
