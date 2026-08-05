package com.connexa.api.domain.identity;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Identity claims that have already been verified by a trusted server-side adapter.
 */
public record VerifiedIdentity(
        IdentityKey key,
        String displayName,
        String email,
        Set<IdentityRole> roles) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public VerifiedIdentity {
        Objects.requireNonNull(key, "key is required");
        displayName = normalizeOptional(displayName);
        email = normalizeEmail(email);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public String issuer() {
        return key.issuer();
    }

    public String subject() {
        return key.subject();
    }

    public boolean hasRole(IdentityRole role) {
        return roles.contains(Objects.requireNonNull(role, "role is required"));
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeEmail(String value) {
        String normalized = normalizeOptional(value);
        if (normalized != null && !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email must be a valid address");
        }
        return normalized;
    }
}
