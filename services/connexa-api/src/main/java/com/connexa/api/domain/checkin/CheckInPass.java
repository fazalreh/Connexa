package com.connexa.api.domain.checkin;

import com.connexa.api.domain.identity.IdentityKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A signed, short-lived pass an attendee presents at the door.
 *
 * <p>The pass is self-contained and carries its own signature, so the scanning device never
 * needs a privileged credential and the door works without the scanner holding any secret.
 *
 * <p>Everything the server needs to trust is inside the signed payload. Reading the identity
 * from the scanned text without verifying it would let anyone check in as anyone else simply
 * by generating their own code.
 */
public final class CheckInPass {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION = "c1";
    private static final char FIELD = '.';
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CheckInPass() {
    }

    /**
     * Encodes a pass valid until {@code expiresAt}.
     *
     * <p>Expiry is what stops a screenshot of someone's code being reusable later, so a pass
     * is issued fresh and kept short-lived rather than stored.
     */
    public static String issue(
            IdentityKey identity, UUID eventId, Instant expiresAt, byte[] secret) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");

        String payload = String.join(String.valueOf(FIELD),
                VERSION,
                eventId.toString(),
                ENCODER.encodeToString(identity.issuer().getBytes(StandardCharsets.UTF_8)),
                ENCODER.encodeToString(identity.subject().getBytes(StandardCharsets.UTF_8)),
                Long.toString(expiresAt.getEpochSecond()));
        return payload + FIELD + ENCODER.encodeToString(sign(payload, secret));
    }

    /**
     * Verifies a scanned pass against the event it is being presented for.
     *
     * @param eventId the event the door is checking in for; a pass for a different event is
     *     rejected even when its signature is valid, so one event's code cannot be replayed
     *     at another
     * @throws InvalidCheckInPassException if malformed, mis-signed, for another event, or expired
     */
    public static IdentityKey verify(String pass, UUID eventId, Instant now, byte[] secret) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(now, "now is required");
        if (pass == null || pass.isBlank()) {
            throw new InvalidCheckInPassException("This code is not readable.");
        }

        String[] parts = pass.trim().split("\\" + FIELD);
        if (parts.length != 6 || !VERSION.equals(parts[0])) {
            throw new InvalidCheckInPassException("This code is not readable.");
        }

        String payload = String.join(String.valueOf(FIELD),
                parts[0], parts[1], parts[2], parts[3], parts[4]);
        byte[] presented;
        try {
            presented = DECODER.decode(parts[5]);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidCheckInPassException("This code is not readable.");
        }
        // Constant time: a byte-by-byte comparison that stops at the first mismatch would
        // leak the correct signature prefix through timing.
        if (!MessageDigest.isEqual(sign(payload, secret), presented)) {
            throw new InvalidCheckInPassException("This code could not be verified.");
        }

        UUID passEventId;
        long expiresAtEpochSecond;
        try {
            passEventId = UUID.fromString(parts[1]);
            expiresAtEpochSecond = Long.parseLong(parts[4]);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidCheckInPassException("This code is not readable.");
        }

        if (!passEventId.equals(eventId)) {
            throw new InvalidCheckInPassException("This code is for a different event.");
        }
        if (now.getEpochSecond() > expiresAtEpochSecond) {
            throw new InvalidCheckInPassException("This code has expired. Ask for a fresh one.");
        }

        return new IdentityKey(
                new String(DECODER.decode(parts[2]), StandardCharsets.UTF_8),
                new String(DECODER.decode(parts[3]), StandardCharsets.UTF_8));
    }

    private static byte[] sign(String payload, byte[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalStateException("A check-in signing secret is required");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign a check-in pass", exception);
        }
    }
}
