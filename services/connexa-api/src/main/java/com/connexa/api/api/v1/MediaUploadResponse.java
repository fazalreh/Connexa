package com.connexa.api.api.v1;

import com.connexa.api.application.MediaUploadService;
import java.time.Instant;

/**
 * Permission to write one image to one location.
 *
 * <p>Deliberately does not carry the account secret. It signs uploads, and a secret placed
 * in a client is readable by anyone who installs that client.
 */
public record MediaUploadResponse(
        String uploadUrl,
        String cloudName,
        String apiKey,
        String signature,
        long timestamp,
        String publicId,
        Instant expiresAt,
        long maxUploadBytes) {

    public static MediaUploadResponse from(MediaUploadService.SignedUpload signed) {
        return new MediaUploadResponse(
                signed.uploadUrl(),
                signed.cloudName(),
                signed.apiKey(),
                signed.signature(),
                signed.timestamp(),
                signed.publicId(),
                signed.expiresAt(),
                signed.maxUploadBytes());
    }
}
