package com.connexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The status endpoint previously reported four hard-coded {@code false} values, so it claimed
 * Firebase and the assistant were disabled while both were configured and serving traffic.
 * These tests pin the report to the settings that actually select the adapters.
 */
class IntegrationAvailabilityTest {

    @Test
    void reportsEverythingDisabledWhenNothingIsConfigured() {
        IntegrationAvailability availability =
                new IntegrationAvailability("rejecting", "disabled", "");

        assertThat(availability.disabledNames())
                .containsExactlyInAnyOrder("identity", "assistant", "announcement-ingestion", "push");
    }

    @Test
    void reportsNothingDisabledWhenEverythingIsConfigured() {
        IntegrationAvailability availability =
                new IntegrationAvailability("firebase", "gemini", "a-shared-token");

        assertThat(availability.disabledNames()).isEmpty();
    }

    @Test
    void aConfiguredAssistantIsNotReportedAsDisabled() {
        IntegrationAvailability availability =
                new IntegrationAvailability("firebase", "gemini", "");

        assertThat(availability.isAssistantEnabled()).isTrue();
        assertThat(availability.disabledNames()).doesNotContain("assistant", "identity");
    }

    @Test
    void pushFollowsIdentityBecauseItSharesTheSameProviderApp() {
        assertThat(new IntegrationAvailability("firebase", "disabled", "").isPushEnabled()).isTrue();
        assertThat(new IntegrationAvailability("rejecting", "gemini", "").isPushEnabled()).isFalse();
    }

    @Test
    void modeComparisonIgnoresCasingAndSurroundingSpace() {
        IntegrationAvailability availability =
                new IntegrationAvailability("  Firebase ", " GEMINI ", "  token  ");

        assertThat(availability.disabledNames()).isEmpty();
    }
}
