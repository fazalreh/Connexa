package com.connexa.api.domain.identity;

import java.util.Objects;

/**
 * Stable, provider-scoped identity used for authorization and per-user state.
 */
public record IdentityKey(String issuer, String subject) {

    public IdentityKey {
        issuer = requireText(issuer, "issuer");
        subject = requireText(subject, "subject");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
