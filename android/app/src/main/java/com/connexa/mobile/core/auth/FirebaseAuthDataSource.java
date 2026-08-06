package com.connexa.mobile.core.auth;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Authenticates against the configured identity provider.
 *
 * <p>The provider is the only thing this class knows about. It issues no roles and grants no
 * permissions: the account role captured at registration is a stated preference, and every
 * authorization decision is made by the server from the verified token.
 *
 * <p>Calls block, and are made from a background executor by the calling feature.
 */
public final class FirebaseAuthDataSource implements AuthDataSource {

    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthDataSource(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = Objects.requireNonNull(firebaseAuth, "firebaseAuth");
    }

    @Override
    public AuthSession signIn(SignInRequest request) throws AuthDataSourceException {
        Objects.requireNonNull(request, "request");
        AuthResult result = await(() -> Tasks.await(firebaseAuth.signInWithEmailAndPassword(
                request.getEmail(), request.getPassword())));
        return sessionOf(requireUser(result), AccountRole.ATTENDEE);
    }

    @Override
    public AuthSession signUp(SignUpRequest request) throws AuthDataSourceException {
        Objects.requireNonNull(request, "request");
        if (!request.isTermsAccepted()) {
            throw new AuthDataSourceException(
                    AuthDataSourceException.Reason.UNKNOWN, "The terms must be accepted.");
        }
        AuthResult result = await(() -> Tasks.await(firebaseAuth.createUserWithEmailAndPassword(
                request.getEmail(), request.getPassword())));
        FirebaseUser user = requireUser(result);

        // Best effort: a profile name is cosmetic, so a failure here must not undo a
        // registration that already succeeded.
        try {
            Tasks.await(user.updateProfile(new UserProfileChangeRequest.Builder()
                    .setDisplayName(request.getFullName())
                    .build()));
        } catch (ExecutionException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return AuthSessionFactory.create(
                user.getUid(), request.getEmail(), request.getFullName(), request.getAccountRole());
    }

    @Override
    public Optional<AuthSession> currentSession() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user == null
                ? Optional.empty()
                : Optional.of(sessionOf(user, AccountRole.ATTENDEE));
    }

    @Override
    public void signOut() {
        firebaseAuth.signOut();
    }

    private static AuthSession sessionOf(FirebaseUser user, AccountRole role) {
        return AuthSessionFactory.create(
                user.getUid(), user.getEmail(), user.getDisplayName(), role);
    }

    private static FirebaseUser requireUser(AuthResult result) throws AuthDataSourceException {
        FirebaseUser user = result == null ? null : result.getUser();
        if (user == null) {
            throw new AuthDataSourceException(
                    AuthDataSourceException.Reason.UNKNOWN, "Could not complete that request.");
        }
        return user;
    }

    @FunctionalInterface
    private interface ProviderCall {
        AuthResult run() throws ExecutionException, InterruptedException;
    }

    private static AuthResult await(ProviderCall call) throws AuthDataSourceException {
        try {
            return call.run();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            AuthDataSourceException.Reason reason = cause instanceof FirebaseAuthException
                    ? AuthFailureReasons.fromErrorCode(((FirebaseAuthException) cause).getErrorCode())
                    : AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE;
            throw new AuthDataSourceException(reason, AuthFailureReasons.messageFor(reason), cause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AuthDataSourceException(
                    AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE,
                    AuthFailureReasons.messageFor(
                            AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE),
                    exception);
        }
    }
}
