package com.connexa.api.domain.user;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserProfile(
        UUID id,
        String displayName,
        String email,
        URI avatarUrl,
        Instant createdAt,
        Instant updatedAt) {

    public UserProfile {
        Objects.requireNonNull(id, "id is required");
        requireText(displayName, "displayName");
        requireText(email, "email");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
