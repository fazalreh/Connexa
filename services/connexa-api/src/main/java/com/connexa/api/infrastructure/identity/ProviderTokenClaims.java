package com.connexa.api.infrastructure.identity;

import java.util.Objects;
import java.util.Set;

/**
 * The claims taken from a verified provider token.
 *
 * <p>Deliberately independent of any provider SDK type. Role resolution is the part most
 * worth testing and the part where a mistake grants someone else's privileges, so it is kept
 * reachable without standing up a provider.
 *
 * @param roleClaims role names asserted by the provider itself, already lowercased
 */
public record ProviderTokenClaims(
        String issuer,
        String subject,
        String email,
        boolean emailVerified,
        String displayName,
        Set<String> roleClaims) {

    public ProviderTokenClaims {
        Objects.requireNonNull(issuer, "issuer is required");
        Objects.requireNonNull(subject, "subject is required");
        roleClaims = roleClaims == null ? Set.of() : Set.copyOf(roleClaims);
    }
}
