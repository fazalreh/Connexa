package com.connexa.mobile.core.organizer;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque acknowledgement returned after an upload intent is accepted.
 *
 * <p>The ticket intentionally exposes no provider-specific route or authorization material.</p>
 */
public final class MediaUploadTicket {

    private final UUID uploadId;
    private final Instant expiresAt;
    private final long maximumContentLengthBytes;

    public MediaUploadTicket(UUID uploadId, Instant expiresAt, long maximumContentLengthBytes) {
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (maximumContentLengthBytes < 1) {
            throw new IllegalArgumentException("maximumContentLengthBytes must be positive");
        }
        this.maximumContentLengthBytes = maximumContentLengthBytes;
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getMaximumContentLengthBytes() {
        return maximumContentLengthBytes;
    }
}
