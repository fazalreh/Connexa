package com.connexa.api.domain.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.connexa.api.domain.identity.IdentityKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckInPassTest {

    private static final byte[] SIGNING_MATERIAL = "check-in-test-signing-material".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_SIGNING_MATERIAL = "a-different-test-signing-material".getBytes(StandardCharsets.UTF_8);
    private static final IdentityKey ALICE = new IdentityKey("https://identity.connexa", "alice");
    private static final Instant NOW = Instant.parse("2026-07-01T17:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(5));

    @Test
    @DisplayName("a freshly issued pass verifies to the attendee who was issued it")
    void roundTripsTheIdentity() {
        UUID eventId = UUID.randomUUID();

        String pass = CheckInPass.issue(ALICE, eventId, EXPIRES, SIGNING_MATERIAL);

        assertThat(CheckInPass.verify(pass, eventId, NOW, SIGNING_MATERIAL)).isEqualTo(ALICE);
    }

    @Test
    @DisplayName("a pass signed with another secret is refused")
    void refusesForeignSignature() {
        UUID eventId = UUID.randomUUID();
        String forged = CheckInPass.issue(ALICE, eventId, EXPIRES, OTHER_SIGNING_MATERIAL);

        assertThatThrownBy(() -> CheckInPass.verify(forged, eventId, NOW, SIGNING_MATERIAL))
                .isInstanceOf(InvalidCheckInPassException.class);
    }

    @Test
    @DisplayName("editing the identity inside a pass invalidates it")
    void refusesTamperedIdentity() {
        // The whole point: someone rewriting the payload to another person's id must not
        // be able to check in as them.
        UUID eventId = UUID.randomUUID();
        String pass = CheckInPass.issue(ALICE, eventId, EXPIRES, SIGNING_MATERIAL);
        String[] parts = pass.split("\\.");
        String tampered = String.join(".", parts[0], parts[1], parts[2],
                java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString("mallory".getBytes(StandardCharsets.UTF_8)),
                parts[4], parts[5]);

        assertThatThrownBy(() -> CheckInPass.verify(tampered, eventId, NOW, SIGNING_MATERIAL))
                .isInstanceOf(InvalidCheckInPassException.class);
    }

    @Test
    @DisplayName("extending the expiry inside a pass invalidates it")
    void refusesTamperedExpiry() {
        UUID eventId = UUID.randomUUID();
        String pass = CheckInPass.issue(ALICE, eventId, EXPIRES, SIGNING_MATERIAL);
        String[] parts = pass.split("\\.");
        String tampered = String.join(".", parts[0], parts[1], parts[2], parts[3],
                Long.toString(EXPIRES.plus(Duration.ofDays(365)).getEpochSecond()), parts[5]);

        assertThatThrownBy(() -> CheckInPass.verify(tampered, eventId, NOW, SIGNING_MATERIAL))
                .isInstanceOf(InvalidCheckInPassException.class);
    }

    @Test
    @DisplayName("a pass for one event cannot be presented at another")
    void refusesReplayAtAnotherEvent() {
        // Both events are real and the signature is genuine; only the binding stops it.
        UUID issuedFor = UUID.randomUUID();
        UUID presentedAt = UUID.randomUUID();
        String pass = CheckInPass.issue(ALICE, issuedFor, EXPIRES, SIGNING_MATERIAL);

        assertThatThrownBy(() -> CheckInPass.verify(pass, presentedAt, NOW, SIGNING_MATERIAL))
                .isInstanceOf(InvalidCheckInPassException.class)
                .hasMessageContaining("different event");
    }

    @Test
    @DisplayName("an expired pass is refused")
    void refusesExpiredPass() {
        UUID eventId = UUID.randomUUID();
        String pass = CheckInPass.issue(ALICE, eventId, EXPIRES, SIGNING_MATERIAL);

        assertThatThrownBy(() ->
                CheckInPass.verify(pass, eventId, EXPIRES.plusSeconds(1), SIGNING_MATERIAL))
                .isInstanceOf(InvalidCheckInPassException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("a pass is still valid in its final second")
    void acceptsPassAtTheExpiryBoundary() {
        UUID eventId = UUID.randomUUID();
        String pass = CheckInPass.issue(ALICE, eventId, EXPIRES, SIGNING_MATERIAL);

        assertThat(CheckInPass.verify(pass, eventId, EXPIRES, SIGNING_MATERIAL)).isEqualTo(ALICE);
    }

    @Test
    @DisplayName("malformed input is refused rather than throwing something unexpected")
    void refusesMalformedInput() {
        UUID eventId = UUID.randomUUID();
        for (String bad : new String[] {"", "   ", "not-a-pass", "c1.only.three.parts",
                "c1.not-a-uuid.aGk.aGk.99.c2ln", "x9.a.b.c.d.e"}) {
            assertThatThrownBy(() -> CheckInPass.verify(bad, eventId, NOW, SIGNING_MATERIAL))
                    .as("input %s", bad)
                    .isInstanceOf(InvalidCheckInPassException.class);
        }
        assertThatThrownBy(() -> CheckInPass.verify(null, eventId, NOW, SIGNING_MATERIAL))
                .isInstanceOf(InvalidCheckInPassException.class);
    }

    @Test
    @DisplayName("identities containing separators survive the round trip")
    void handlesIdentitiesContainingSeparators() {
        // Issuers are URLs and contain dots, so the fields must be encoded rather than
        // concatenated raw.
        IdentityKey awkward = new IdentityKey(
                "https://securetoken.google.com/connexa-3dc6b", "uid.with.dots");
        UUID eventId = UUID.randomUUID();

        String pass = CheckInPass.issue(awkward, eventId, EXPIRES, SIGNING_MATERIAL);

        assertThat(CheckInPass.verify(pass, eventId, NOW, SIGNING_MATERIAL)).isEqualTo(awkward);
    }

    @Test
    @DisplayName("two attendees never share a pass")
    void passesAreDistinctPerAttendee() {
        UUID eventId = UUID.randomUUID();
        IdentityKey bob = new IdentityKey("https://identity.connexa", "bob");

        assertThat(CheckInPass.issue(ALICE, eventId, EXPIRES, SIGNING_MATERIAL))
                .isNotEqualTo(CheckInPass.issue(bob, eventId, EXPIRES, SIGNING_MATERIAL));
    }

    @Test
    @DisplayName("signing without a configured secret fails loudly")
    void requiresASecret() {
        // Falling back to an empty key would make every pass forgeable by anyone.
        assertThatThrownBy(() -> CheckInPass.issue(ALICE, UUID.randomUUID(), EXPIRES, new byte[0]))
                .isInstanceOf(IllegalStateException.class);
    }
}
