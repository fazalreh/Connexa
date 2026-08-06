package com.connexa.mobile.core.auth;

import java.util.Optional;

/**
 * Refuses authentication when no identity provider is configured for this build.
 *
 * <p>Returned instead of null so callers have one code path: a build without provider
 * configuration reports the same "temporarily unavailable" outcome as a provider outage,
 * rather than crashing or silently appearing signed out.
 */
public final class UnavailableAuthDataSource implements AuthDataSource {

    @Override
    public AuthSession signIn(SignInRequest request) throws AuthDataSourceException {
        throw unavailable();
    }

    @Override
    public AuthSession signUp(SignUpRequest request) throws AuthDataSourceException {
        throw unavailable();
    }

    @Override
    public Optional<AuthSession> currentSession() {
        return Optional.empty();
    }

    @Override
    public void signOut() {
        // Nothing was ever established, so there is nothing to clear.
    }

    private static AuthDataSourceException unavailable() {
        return new AuthDataSourceException(
                AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE,
                AuthFailureReasons.messageFor(
                        AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE));
    }
}
