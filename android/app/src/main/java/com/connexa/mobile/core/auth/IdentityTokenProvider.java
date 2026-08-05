package com.connexa.mobile.core.auth;

import java.io.IOException;

/**
 * Supplies a short-lived, server-verifiable bearer token for protected Connexa API calls.
 *
 * <p>Implementations must obtain tokens from the configured identity provider at runtime. They
 * must not embed privileged credentials or long-lived tokens in the Android app.</p>
 */
public interface IdentityTokenProvider {

    String currentBearerToken() throws IOException;
}
