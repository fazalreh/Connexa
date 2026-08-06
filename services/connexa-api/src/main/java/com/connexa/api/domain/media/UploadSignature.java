package com.connexa.api.domain.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Signs a direct-to-storage upload.
 *
 * <p>The file travels from the phone to the storage provider without passing through
 * this service, which keeps large images off the API. What makes that safe is this
 * signature: it covers the exact parameters the client must send, so the client cannot
 * change where the file lands or what it overwrites without invalidating it.
 *
 * <p>The parameters are ordered by name before hashing because the provider recomputes
 * the same digest on its side and only an identical byte sequence will match.
 */
public final class UploadSignature {

    private static final String DIGEST_ALGORITHM = "SHA-1";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private UploadSignature() {
    }

    /**
     * Returns the hex digest the client must present alongside the signed parameters.
     *
     * @param parameters everything being signed; the file itself is never included,
     *     since the provider signs the request rather than the content
     * @param apiSecret stays on this server — a client that held it could sign uploads
     *     to any location under the account
     */
    public static String sign(Map<String, String> parameters, String apiSecret) {
        Objects.requireNonNull(parameters, "parameters is required");
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("apiSecret is required to sign an upload");
        }
        return hex(digest(canonicalize(parameters) + apiSecret));
    }

    /**
     * Joins the parameters into the one form both sides agree to hash.
     *
     * <p>Blank values are dropped rather than signed as empty: the client omits them, so
     * signing them would produce a digest the provider could never reproduce.
     */
    static String canonicalize(Map<String, String> parameters) {
        SortedMap<String, String> ordered = new TreeMap<>(parameters);
        StringJoiner joined = new StringJoiner("&");
        for (Map.Entry<String, String> parameter : ordered.entrySet()) {
            String value = parameter.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            joined.add(parameter.getKey() + "=" + value);
        }
        return joined.toString();
    }

    private static byte[] digest(String payload) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM)
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException unavailable) {
            // Required of every Java platform, so absence is a broken runtime rather
            // than a condition a caller could handle.
            throw new IllegalStateException(DIGEST_ALGORITHM + " is unavailable", unavailable);
        }
    }

    private static String hex(byte[] bytes) {
        char[] characters = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            characters[index * 2] = HEX[value >>> 4];
            characters[index * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(characters);
    }
}
