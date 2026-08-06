package com.connexa.api.application;

import com.connexa.api.config.MediaProperties;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.media.MediaDeliveryUrl;
import com.connexa.api.domain.media.MediaUnavailableException;
import com.connexa.api.domain.media.MediaUpload;
import com.connexa.api.domain.media.UploadSignature;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Authorises organizer image uploads and signs them.
 *
 * <p>The image itself never passes through this service. Routing tens of megabytes of photos
 * through the API would spend its memory and bandwidth on bytes it does not need to see, so
 * the phone uploads directly to storage and this service only says what may be uploaded and
 * where it may land.
 */
@Service
public class MediaUploadService {

    /**
     * How long a signature stays usable.
     *
     * <p>Long enough to send a photo over a slow connection, short enough that one captured
     * from a device is not an open-ended write credential for the account.
     */
    private static final long SIGNATURE_VALIDITY_SECONDS = 600L;

    private final MediaProperties properties;
    private final Clock clock;

    /**
     * With a second constructor present for clock injection, the container cannot pick one
     * on its own, so the injection point is stated rather than left to chance.
     */
    @Autowired
    public MediaUploadService(MediaProperties properties) {
        this(properties, Clock.systemUTC());
    }

    MediaUploadService(MediaProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Grants one organizer permission to write one image to one generated location.
     *
     * <p>The reply carries the account's public key but never its secret. The secret is what
     * signs uploads, and an application package is readable by anyone who installs it, so a
     * secret shipped inside one would be a published secret.
     */
    public SignedUpload authorizeUpload(VerifiedIdentity identity, MediaUpload upload) {
        if (!properties.isEnabled()) {
            throw new MediaUnavailableException();
        }
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(upload, "upload is required");
        if (!identity.hasRole(IdentityRole.ORGANIZER)) {
            throw new ActorNotAuthorizedException("upload event images");
        }
        upload.requireWithin(properties.maxUploadBytes());

        Instant now = clock.instant();
        long timestamp = now.getEpochSecond();
        String publicId = upload.publicIdUnder(properties.folder());

        // Only these parameters are signed, so only these may be sent. Anything the client
        // adds — a different name, a different folder, an overwrite flag — breaks the digest
        // and storage refuses the upload.
        Map<String, String> signed = new LinkedHashMap<>();
        signed.put("public_id", publicId);
        signed.put("timestamp", Long.toString(timestamp));

        return new SignedUpload(
                properties.uploadUrl(),
                properties.cloudName(),
                properties.apiKey(),
                UploadSignature.sign(signed, properties.apiSecret()),
                timestamp,
                publicId,
                now.plusSeconds(SIGNATURE_VALIDITY_SECONDS),
                properties.maxUploadBytes());
    }

    /**
     * Confirms that a URL the client reports really is the upload that was authorised.
     *
     * @throws com.connexa.api.domain.media.UnsupportedMediaException if it is anything else
     */
    public String confirmUpload(String reportedUrl, String publicId) {
        if (!properties.isEnabled()) {
            throw new MediaUnavailableException();
        }
        return MediaDeliveryUrl.verify(reportedUrl, properties.deliveryPrefix(), publicId);
    }

    /**
     * Everything the client needs to perform the upload, and nothing more.
     *
     * @param apiKey identifies the account and is safe to publish; the secret that signs
     *     uploads is deliberately absent
     * @param expiresAt lets a client re-request rather than start a transfer that storage
     *     will reject on arrival
     */
    public record SignedUpload(
            String uploadUrl,
            String cloudName,
            String apiKey,
            String signature,
            long timestamp,
            String publicId,
            Instant expiresAt,
            long maxUploadBytes) {
    }
}
