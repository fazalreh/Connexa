package com.connexa.api.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.connexa.api.config.MediaProperties;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.media.MediaUnavailableException;
import com.connexa.api.domain.media.MediaUpload;
import com.connexa.api.domain.media.UnsupportedMediaException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MediaUploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-01T09:00:00Z");
    private static final MediaProperties CONFIGURED = new MediaProperties(
            "connexa-demo", "123456789012345", "test-signing-secret", "connexa/events", 10_485_760L);
    private static final MediaProperties UNCONFIGURED =
            new MediaProperties("", "", "", "connexa/events", 10_485_760L);

    private static VerifiedIdentity identity(IdentityRole... roles) {
        return new VerifiedIdentity(
                new IdentityKey("firebase", "uid-1"),
                "Ayesha Khan",
                "organizer@connexa.test",
                Set.of(roles));
    }

    private static MediaUploadService serviceWith(MediaProperties properties) {
        return new MediaUploadService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MediaUpload aPhoto() {
        return new MediaUpload("image/jpeg", 2_000_000L);
    }

    @Test
    void signsAnUploadForAnOrganizer() {
        MediaUploadService.SignedUpload signed =
                serviceWith(CONFIGURED).authorizeUpload(identity(IdentityRole.ORGANIZER), aPhoto());

        assertThat(signed.signature()).matches("[0-9a-f]{40}");
        assertThat(signed.timestamp()).isEqualTo(NOW.getEpochSecond());
        assertThat(signed.uploadUrl())
                .isEqualTo("https://api.cloudinary.com/v1_1/connexa-demo/image/upload");
    }

    @Test
    void neverReturnsTheAccountSecret() {
        // The reply is delivered to a phone. Anything in it is readable by whoever holds
        // that phone, so the value that signs uploads must not appear anywhere in it.
        MediaUploadService.SignedUpload signed =
                serviceWith(CONFIGURED).authorizeUpload(identity(IdentityRole.ORGANIZER), aPhoto());

        assertThat(signed.toString()).doesNotContain("test-signing-secret");
        assertThat(signed.signature()).isNotEqualTo("test-signing-secret");
    }

    @Test
    void placesTheAssetInsideTheConfiguredFolder() {
        MediaUploadService.SignedUpload signed =
                serviceWith(CONFIGURED).authorizeUpload(identity(IdentityRole.ORGANIZER), aPhoto());

        assertThat(signed.publicId()).startsWith("connexa/events/");
    }

    @Test
    void generatesADifferentLocationEachTime() {
        // A name the client could predict would be a name it could aim at someone else's.
        MediaUploadService service = serviceWith(CONFIGURED);
        VerifiedIdentity organizer = identity(IdentityRole.ORGANIZER);

        assertThat(service.authorizeUpload(organizer, aPhoto()).publicId())
                .isNotEqualTo(service.authorizeUpload(organizer, aPhoto()).publicId());
    }

    @Test
    void anAttendeeCannotUpload() {
        assertThatThrownBy(() -> serviceWith(CONFIGURED)
                        .authorizeUpload(identity(IdentityRole.ATTENDEE), aPhoto()))
                .isInstanceOf(ActorNotAuthorizedException.class);
    }

    @Test
    void refusesWhenNoAccountIsConfigured() {
        assertThatThrownBy(() -> serviceWith(UNCONFIGURED)
                        .authorizeUpload(identity(IdentityRole.ORGANIZER), aPhoto()))
                .isInstanceOf(MediaUnavailableException.class);
    }

    @Test
    void refusesAFileLargerThanTheLimit() {
        assertThatThrownBy(() -> serviceWith(CONFIGURED).authorizeUpload(
                        identity(IdentityRole.ORGANIZER),
                        new MediaUpload("image/jpeg", 20_000_000L)))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void refusesAScriptableImageFormat() {
        // Served from the delivery host, an SVG runs in that host's origin.
        assertThatThrownBy(() -> new MediaUpload("image/svg+xml", 1_000L))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void refusesSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> new MediaUpload("application/pdf", 1_000L))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void confirmsTheAddressTheAuthorisedUploadProduced() {
        MediaUploadService service = serviceWith(CONFIGURED);
        MediaUploadService.SignedUpload signed =
                service.authorizeUpload(identity(IdentityRole.ORGANIZER), aPhoto());
        String url = "https://res.cloudinary.com/connexa-demo/image/upload/v1719830400/"
                + signed.publicId() + ".jpg";

        assertThat(service.confirmUpload(url, signed.publicId())).isEqualTo(url);
    }

    @Test
    void refusesToConfirmAnAddressFromSomewhereElse() {
        MediaUploadService service = serviceWith(CONFIGURED);
        MediaUploadService.SignedUpload signed =
                service.authorizeUpload(identity(IdentityRole.ORGANIZER), aPhoto());

        assertThatThrownBy(() -> service.confirmUpload(
                        "https://example.com/anything.jpg", signed.publicId()))
                .isInstanceOf(UnsupportedMediaException.class);
    }
}
