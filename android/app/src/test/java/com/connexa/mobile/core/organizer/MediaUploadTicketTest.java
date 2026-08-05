package com.connexa.mobile.core.organizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.Test;

public class MediaUploadTicketTest {

    @Test
    public void retainsOnlyOpaqueUploadState() {
        UUID uploadId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-09-10T12:00:00Z");

        MediaUploadTicket ticket = new MediaUploadTicket(uploadId, expiresAt, 5_000);

        assertEquals(uploadId, ticket.getUploadId());
        assertEquals(expiresAt, ticket.getExpiresAt());
        assertEquals(5_000, ticket.getMaximumContentLengthBytes());
    }

    @Test
    public void rejectsANonPositiveLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MediaUploadTicket(
                        UUID.randomUUID(),
                        Instant.parse("2026-09-10T12:00:00Z"),
                        0));
    }
}
