package com.connexa.api.application;

import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.identity.VerifiedIdentityRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * Single application boundary for obtaining the authenticated actor.
 */
@Service
public class IdentityAccessService {

    public VerifiedIdentity requireVerifiedIdentity(HttpServletRequest request) {
        return VerifiedIdentityRequest.require(request);
    }
}
