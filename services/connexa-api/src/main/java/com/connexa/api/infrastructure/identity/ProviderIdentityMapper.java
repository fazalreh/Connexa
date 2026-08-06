package com.connexa.api.infrastructure.identity;

import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns verified token claims into the roles the application authorizes against.
 *
 * <p>Two independent sources can grant {@link IdentityRole#ORGANIZER}: a role asserted by the
 * provider, or an operator-configured allowlist. Supporting both means the deployment is not
 * forced to choose up front, and the allowlist can be retired later without a code change.
 *
 * <p>The allowlist matches on email, and with email/password sign-up anyone can register any
 * address. An unverified address is therefore an unproven claim of ownership and must not
 * satisfy the allowlist — otherwise an attacker simply registers the organizer's address.
 * Provider-asserted roles carry no such risk, since only an administrator can set them.
 */
public final class ProviderIdentityMapper {

    private final Set<String> organizerEmails;
    private final boolean requireVerifiedEmail;

    public ProviderIdentityMapper(Collection<String> organizerEmails, boolean requireVerifiedEmail) {
        this.organizerEmails = Objects.requireNonNull(organizerEmails, "organizerEmails is required")
                .stream()
                .filter(Objects::nonNull)
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.requireVerifiedEmail = requireVerifiedEmail;
    }

    public VerifiedIdentity toVerifiedIdentity(ProviderTokenClaims claims) {
        Objects.requireNonNull(claims, "claims is required");

        Set<IdentityRole> roles = EnumSet.of(IdentityRole.ATTENDEE);
        roles.addAll(providerAssertedRoles(claims));
        if (allowlistGrantsOrganizer(claims)) {
            roles.add(IdentityRole.ORGANIZER);
        }

        return new VerifiedIdentity(
                new IdentityKey(claims.issuer(), claims.subject()),
                claims.displayName(),
                claims.email(),
                roles);
    }

    /**
     * Roles the provider itself asserted. Unrecognised names are ignored rather than
     * rejected, so adding a role upstream cannot lock existing users out.
     */
    private static Set<IdentityRole> providerAssertedRoles(ProviderTokenClaims claims) {
        Set<IdentityRole> roles = EnumSet.noneOf(IdentityRole.class);
        for (String claimed : claims.roleClaims()) {
            for (IdentityRole role : IdentityRole.values()) {
                if (role.name().equalsIgnoreCase(claimed)) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    private boolean allowlistGrantsOrganizer(ProviderTokenClaims claims) {
        String email = claims.email();
        if (email == null || email.isBlank()) {
            return false;
        }
        if (requireVerifiedEmail && !claims.emailVerified()) {
            return false;
        }
        return organizerEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
