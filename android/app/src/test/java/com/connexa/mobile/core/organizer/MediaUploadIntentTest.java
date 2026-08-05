package com.connexa.mobile.core.organizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.UUID;
import org.junit.Test;

public class MediaUploadIntentTest {

    @Test
    public void acceptsImageMetadataWithoutAFilePathOrProviderDetail() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        MediaUploadIntent intent = new MediaUploadIntent(
                eventId,
                requestId,
                OrganizerMediaType.COVER_IMAGE,
                "event-cover.png",
                "IMAGE/PNG",
                1_024);

        assertEquals(eventId, intent.getEventId());
        assertEquals(requestId, intent.getRequestId());
        assertEquals("image/png", intent.getMimeType());
        assertEquals("event-cover.png", intent.getFileName());
    }

    @Test
    public void rejectsImageRolesWithNonImageMediaTypes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MediaUploadIntent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OrganizerMediaType.GALLERY_IMAGE,
                        "agenda.pdf",
                        "application/pdf",
                        1_024));
    }

    @Test
    public void rejectsAPathInsteadOfAFileName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MediaUploadIntent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        OrganizerMediaType.ATTACHMENT,
                        "folder/agenda.pdf",
                        "application/pdf",
                        1_024));
    }
}
