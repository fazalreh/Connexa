package com.connexa.api.infrastructure.identity;

import com.connexa.api.domain.identity.AuthenticationRequiredException;
import com.connexa.api.domain.identity.VerifiedIdentity;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Secure default used until a trusted identity-verification adapter is configured.
 */
public final class RejectingIdentityVerifier implements IdentityVerifier {

    @Override
    public VerifiedIdentity verify(HttpServletRequest request) {
        throw new AuthenticationRequiredException();
    }
}
