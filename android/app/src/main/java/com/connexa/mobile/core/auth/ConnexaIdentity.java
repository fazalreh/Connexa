package com.connexa.mobile.core.auth;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Objects;

/**
 * Single place that decides which identity implementation the app runs with.
 *
 * <p>Provider configuration arrives at build time through a file that is never committed, so
 * whether identity is available is a property of the build rather than something a screen can
 * assume. Centralising the decision means no feature constructs a provider directly, and a
 * build without configuration degrades to the fail-closed implementations everywhere at once
 * instead of crashing on whichever screen is opened first.
 */
public final class ConnexaIdentity {

    private ConnexaIdentity() {
    }

    /**
     * True when provider configuration was present at build time. The configuration plugin
     * registers a default app on startup; no registered app means no configuration.
     */
    public static boolean isConfigured(Context context) {
        Objects.requireNonNull(context, "context");
        return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
    }

    /** Supplies bearer tokens for protected API calls, or refuses when unconfigured. */
    public static IdentityTokenProvider tokenProvider(Context context) {
        return isConfigured(context)
                ? new FirebaseIdentityTokenProvider(FirebaseAuth.getInstance())
                : new UnavailableIdentityTokenProvider();
    }

    /** Handles sign-in and registration, or refuses when unconfigured. */
    public static AuthDataSource authDataSource(Context context) {
        return isConfigured(context)
                ? new FirebaseAuthDataSource(FirebaseAuth.getInstance())
                : new UnavailableAuthDataSource();
    }
}
