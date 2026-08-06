package com.connexa.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.connexa.api.config.IngestionProperties;
import com.connexa.api.domain.event.EventStatus;
import com.connexa.api.domain.event.EventSummary;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.infrastructure.ingestion.IngestedEventStore;
import com.connexa.api.infrastructure.ingestion.IngestionOutcome;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestionServiceTest {

    private static final Instant STARTS = Instant.parse("2026-07-01T10:00:00Z");

    /** Records what reached the store so submission mapping can be asserted. */
    private static final class RecordingStore implements IngestedEventStore {
        EventSummary stored;
        Integer capacity;

        @Override
        public IngestionOutcome storeOnce(
                String sourceSystem,
                String sourceRecordId,
                String contentHash,
                EventSummary event,
                Integer totalCapacity) {
            this.stored = event;
            this.capacity = totalCapacity;
            return new IngestionOutcome(event, true);
        }
    }

    @Test
    @DisplayName("the configured token is accepted")
    void acceptsConfiguredToken() {
        IngestionService service = serviceWith("s3cret", new RecordingStore());

        assertThatCode(() -> service.requireIngestionAuthority("s3cret")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a wrong token is refused")
    void refusesWrongToken() {
        IngestionService service = serviceWith("s3cret", new RecordingStore());

        assertThatThrownBy(() -> service.requireIngestionAuthority("guess"))
                .isInstanceOf(ActorNotAuthorizedException.class);
    }

    @Test
    @DisplayName("a token sharing a prefix is refused")
    void refusesPrefixToken() {
        IngestionService service = serviceWith("s3cret", new RecordingStore());

        assertThatThrownBy(() -> service.requireIngestionAuthority("s3cre"))
                .isInstanceOf(ActorNotAuthorizedException.class);
    }

    @Test
    @DisplayName("a missing token is refused")
    void refusesMissingToken() {
        IngestionService service = serviceWith("s3cret", new RecordingStore());

        assertThatThrownBy(() -> service.requireIngestionAuthority(null))
                .isInstanceOf(ActorNotAuthorizedException.class);
        assertThatThrownBy(() -> service.requireIngestionAuthority(""))
                .isInstanceOf(ActorNotAuthorizedException.class);
    }

    @Test
    @DisplayName("intake is closed entirely when no token is configured")
    void refusesEverythingWhenDisabled() {
        // An unconfigured deployment must not accept an empty token as a match.
        IngestionService service = serviceWith("", new RecordingStore());

        assertThatThrownBy(() -> service.requireIngestionAuthority(""))
                .isInstanceOf(ActorNotAuthorizedException.class);
        assertThatThrownBy(() -> service.requireIngestionAuthority("anything"))
                .isInstanceOf(ActorNotAuthorizedException.class);
    }

    @Test
    @DisplayName("a submission becomes a published event")
    void submissionIsPublished() {
        RecordingStore store = new RecordingStore();
        IngestionService service = serviceWith("s3cret", store);

        service.submit("imap", "<a@x>", "hash", "Robotics Workshop",
                "A hands-on session.", STARTS, STARTS.plusSeconds(7_200),
                "Asia/Karachi", "Main Auditorium", "Technology", "Robotics Society", 40);

        assertThat(store.stored.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(store.stored.title()).isEqualTo("Robotics Workshop");
        assertThat(store.stored.venueName()).isEqualTo("Main Auditorium");
        assertThat(store.stored.organizerName()).isEqualTo("Robotics Society");
        assertThat(store.capacity).isEqualTo(40);
    }

    @Test
    @DisplayName("a submission without an organiser falls back to the configured name")
    void fallsBackToConfiguredOrganizer() {
        RecordingStore store = new RecordingStore();
        IngestionService service = serviceWith("s3cret", store);

        service.submit("imap", "<a@x>", "hash", "Open Lecture",
                "Everyone welcome.", STARTS, STARTS.plusSeconds(3_600),
                "Asia/Karachi", "Hall", "Arts", null, null);

        assertThat(store.stored.organizerName()).isEqualTo("Campus Announcements");
    }

    private static IngestionService serviceWith(String token, IngestedEventStore store) {
        return new IngestionService(store, new IngestionProperties(token, null));
    }
}
