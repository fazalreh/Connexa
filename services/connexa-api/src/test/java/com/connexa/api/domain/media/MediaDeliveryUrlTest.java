package com.connexa.api.domain.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The address of an upload comes back through the client, so every one of these cases is
 * something a client could send. What is accepted here is what ends up on a public page.
 */
class MediaDeliveryUrlTest {

    private static final String PREFIX = "https://res.cloudinary.com/connexa-demo/";
    private static final String PUBLIC_ID = "connexa/events/8f14e45f-ceea-467a-9575-8d4d1e0c8d31";
    private static final String VALID =
            PREFIX + "image/upload/v1719830400/" + PUBLIC_ID + ".jpg";

    @Test
    void acceptsTheAddressTheAuthorisedUploadProduces() {
        assertThat(MediaDeliveryUrl.verify(VALID, PREFIX, PUBLIC_ID)).isEqualTo(VALID);
    }

    @Test
    void acceptsAnAddressWithoutAVersionSegment() {
        String url = PREFIX + "image/upload/" + PUBLIC_ID + ".png";

        assertThat(MediaDeliveryUrl.verify(url, PREFIX, PUBLIC_ID)).isEqualTo(url);
    }

    @Test
    void acceptsWhicheverExtensionStorageAssigned() {
        // The extension follows the file's actual content, which this service never saw.
        String url = PREFIX + "image/upload/v1719830400/" + PUBLIC_ID + ".webp";

        assertThat(MediaDeliveryUrl.verify(url, PREFIX, PUBLIC_ID)).isEqualTo(url);
    }

    @Test
    void rejectsAnotherSite() {
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(
                        "https://example.com/tracking-pixel.png", PREFIX, PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsAnotherAccountOnTheSameProvider() {
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(
                        "https://res.cloudinary.com/someone-else/image/upload/" + PUBLIC_ID + ".jpg",
                        PREFIX,
                        PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsADifferentAssetInTheSameAccount() {
        // Otherwise one organizer could set their cover to another's uploaded image.
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(
                        PREFIX + "image/upload/v1719830400/connexa/events/someone-elses.jpg",
                        PREFIX,
                        PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsAnInjectedTransformation() {
        // A transformation is an instruction to the delivery host. This service did not
        // sign one, so a URL carrying one is not the upload that was authorised.
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(
                        PREFIX + "image/upload/l_text:Arial_60:anything/v1719830400/"
                                + PUBLIC_ID + ".jpg",
                        PREFIX,
                        PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsPlainHttp() {
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(
                        "http://res.cloudinary.com/connexa-demo/image/upload/" + PUBLIC_ID + ".jpg",
                        PREFIX,
                        PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsAnAddressThatMerelyStartsTheSameWay() {
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(
                        "https://res.cloudinary.com.attacker.example/connexa-demo/image/upload/"
                                + PUBLIC_ID + ".jpg",
                        PREFIX,
                        PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsATraversalOutOfTheFolder() {
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(
                        PREFIX + "image/upload/v1719830400/connexa/events/../../elsewhere.jpg",
                        PREFIX,
                        PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsNothingAtAll() {
        assertThatThrownBy(() -> MediaDeliveryUrl.verify(null, PREFIX, PUBLIC_ID))
                .isInstanceOf(UnsupportedMediaException.class);
    }
}
