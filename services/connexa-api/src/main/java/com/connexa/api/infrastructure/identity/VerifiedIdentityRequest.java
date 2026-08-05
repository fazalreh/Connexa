package com.connexa.api.infrastructure.identity;

import com.connexa.api.domain.identity.AuthenticationRequiredException;
import com.connexa.api.domain.identity.VerifiedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

/**
 * Holds the identity verified by the request filter for the lifetime of one HTTP request.
 */
public final class VerifiedIdentityRequest {

    private static final String ATTRIBUTE_NAME =
            VerifiedIdentityRequest.class.getName() + ".verifiedIdentity";

    private VerifiedIdentityRequest() {
    }

    public static void attach(HttpServletRequest request, VerifiedIdentity identity) {
        Objects.requireNonNull(request, "request is required")
                .setAttribute(ATTRIBUTE_NAME, Objects.requireNonNull(identity, "identity is required"));
    }

    public static VerifiedIdentity require(HttpServletRequest request) {
        Object value = Objects.requireNonNull(request, "request is required")
                .getAttribute(ATTRIBUTE_NAME);
        if (value instanceof VerifiedIdentity identity) {
            return identity;
        }
        throw new AuthenticationRequiredException();
    }
}
