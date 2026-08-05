package com.connexa.api.infrastructure.identity;

import com.connexa.api.domain.identity.VerifiedIdentity;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifies a request through a trusted server-side identity mechanism.
 * Implementations must not infer an identity from client-controlled headers.
 */
public interface IdentityVerifier {

    VerifiedIdentity verify(HttpServletRequest request);
}
