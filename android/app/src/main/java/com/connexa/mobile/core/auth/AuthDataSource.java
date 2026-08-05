package com.connexa.mobile.core.auth;

import java.util.Optional;

/**
 * Boundary between authentication features and their identity provider.
 *
 * <p>Implementations may use the Connexa service or another approved identity provider. Feature
 * code depends on this interface so it does not need provider-specific SDKs or configuration.</p>
 */
public interface AuthDataSource {

    AuthSession signIn(SignInRequest request) throws AuthDataSourceException;

    AuthSession signUp(SignUpRequest request) throws AuthDataSourceException;

    Optional<AuthSession> currentSession() throws AuthDataSourceException;

    void signOut() throws AuthDataSourceException;
}
