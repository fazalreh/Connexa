package com.connexa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.connexa.api.infrastructure.assistant.AssistantGateway;
import com.connexa.api.infrastructure.assistant.UnavailableAssistantGateway;
import com.connexa.api.infrastructure.attendance.AttendanceStore;
import com.connexa.api.infrastructure.attendance.InMemoryAttendanceStore;
import com.connexa.api.infrastructure.event.EventCatalog;
import com.connexa.api.infrastructure.event.InMemoryEventCatalog;
import com.connexa.api.infrastructure.identity.IdentityVerifier;
import com.connexa.api.infrastructure.identity.RejectingIdentityVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Guards the test environment against inheriting real configuration.
 *
 * <p>The application imports {@code .env.local} when present, which is what lets a developer
 * run the service without exporting anything. A test run picking that file up would silently
 * point the suite at production credentials — it did exactly that once, and a controller test
 * started reading rows from the deployment database. The build now redirects the import to a
 * path that cannot exist; these assertions fail loudly if that ever stops working.
 */
@SpringBootTest
class HermeticTestEnvironmentTest {

    @Autowired
    private Environment environment;

    @Autowired
    private EventCatalog eventCatalog;

    @Autowired
    private AttendanceStore attendanceStore;

    @Autowired
    private IdentityVerifier identityVerifier;

    @Autowired
    private AssistantGateway assistantGateway;

    @Test
    @DisplayName("tests run on the fail-closed defaults, never a configured mode")
    void runsOnFailClosedDefaults() {
        assertThat(environment.getProperty("connexa.persistence.mode", "in-memory"))
                .isEqualTo("in-memory");
        assertThat(environment.getProperty("connexa.identity.mode", "rejecting"))
                .isEqualTo("rejecting");
        assertThat(environment.getProperty("connexa.assistant.mode", "disabled"))
                .isEqualTo("disabled");
    }

    @Test
    @DisplayName("no real adapter is wired into a test context")
    void onlyDefaultAdaptersAreWired() {
        assertThat(eventCatalog).isInstanceOf(InMemoryEventCatalog.class);
        assertThat(attendanceStore).isInstanceOf(InMemoryAttendanceStore.class);
        assertThat(identityVerifier).isInstanceOf(RejectingIdentityVerifier.class);
        assertThat(assistantGateway).isInstanceOf(UnavailableAssistantGateway.class);
    }

    @Test
    @DisplayName("no credential from the local env file reached the test context")
    void noLocalCredentialsLeakedIntoTests() {
        assertThat(environment.getProperty("connexa.assistant.api-key", "")).isEmpty();
        assertThat(environment.getProperty("connexa.identity.firebase.service-account-path", ""))
                .isEmpty();
        assertThat(environment.getProperty("spring.datasource.url", ""))
                .doesNotContain("neon.tech");
        assertThat(environment.getProperty("spring.flyway.enabled", "false")).isEqualTo("false");
    }
}
