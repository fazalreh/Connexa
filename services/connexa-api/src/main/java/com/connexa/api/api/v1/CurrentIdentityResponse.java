package com.connexa.api.api.v1;

import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import java.util.Set;

/**
 * Safe account view derived only from a verified server-side identity.
 */
public record CurrentIdentityResponse(
        String subject,
        String displayName,
        String email,
        Set<IdentityRole> roles) {

    public static CurrentIdentityResponse from(VerifiedIdentity identity) {
        return new CurrentIdentityResponse(
                identity.subject(),
                identity.displayName(),
                identity.email(),
                identity.roles());
    }
}
