package com.connexa.mobile.core.auth;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Supplies the signed-in user's current ID token for protected API calls.
 *
 * <p>Tokens are fetched from the provider on demand rather than cached by this class. The SDK
 * already caches a valid token and refreshes it when it is close to expiry, so caching again
 * here would only create a second copy that can go stale — and a stale token reaches the
 * server as a plain 401 that the user cannot act on.
 *
 * <p>This is called from the networking layer, which never runs on the main thread.
 */
public final class FirebaseIdentityTokenProvider implements IdentityTokenProvider {

    private final FirebaseAuth firebaseAuth;

    public FirebaseIdentityTokenProvider(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = Objects.requireNonNull(firebaseAuth, "firebaseAuth");
    }

    @Override
    public String currentBearerToken() throws IOException {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            throw new IOException("Sign in to continue.");
        }
        try {
            // false: use the cached token unless it has expired. Forcing a refresh on every
            // request would add a network round trip to each API call.
            GetTokenResult result = Tasks.await(user.getIdToken(false));
            String token = result == null ? null : result.getToken();
            if (token == null || token.isEmpty()) {
                throw new IOException("Secure account access is unavailable.");
            }
            return token;
        } catch (ExecutionException exception) {
            throw new IOException("Could not confirm your session.", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Session check was interrupted.", exception);
        }
    }
}
