package com.connexa.mobile.core.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class AuthFailureReasonsTest {

    @Test
    public void wrongPasswordAndUnknownAccountAreIndistinguishable() {
        // Reporting these differently would let the sign-in form reveal which addresses
        // are registered.
        assertEquals(
                AuthFailureReasons.fromErrorCode("ERROR_WRONG_PASSWORD"),
                AuthFailureReasons.fromErrorCode("ERROR_USER_NOT_FOUND"));
        assertEquals(
                AuthDataSourceException.Reason.INVALID_CREDENTIALS,
                AuthFailureReasons.fromErrorCode("ERROR_WRONG_PASSWORD"));
    }

    @Test
    public void malformedEmailIsAlsoJustInvalidCredentials() {
        assertEquals(
                AuthDataSourceException.Reason.INVALID_CREDENTIALS,
                AuthFailureReasons.fromErrorCode("ERROR_INVALID_EMAIL"));
    }

    @Test
    public void duplicateRegistrationIsReported() {
        assertEquals(
                AuthDataSourceException.Reason.ACCOUNT_ALREADY_EXISTS,
                AuthFailureReasons.fromErrorCode("ERROR_EMAIL_ALREADY_IN_USE"));
    }

    @Test
    public void expiredTokenIsASessionProblem() {
        assertEquals(
                AuthDataSourceException.Reason.SESSION_EXPIRED,
                AuthFailureReasons.fromErrorCode("ERROR_USER_TOKEN_EXPIRED"));
    }

    @Test
    public void throttlingAndNetworkFailuresAreTemporary() {
        assertEquals(
                AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE,
                AuthFailureReasons.fromErrorCode("ERROR_TOO_MANY_REQUESTS"));
        assertEquals(
                AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE,
                AuthFailureReasons.fromErrorCode("ERROR_NETWORK_REQUEST_FAILED"));
    }

    @Test
    public void unrecognisedAndMissingCodesFallBackSafely() {
        assertEquals(
                AuthDataSourceException.Reason.UNKNOWN,
                AuthFailureReasons.fromErrorCode("ERROR_SOMETHING_NEW"));
        assertEquals(
                AuthDataSourceException.Reason.UNKNOWN,
                AuthFailureReasons.fromErrorCode(null));
    }

    @Test
    public void messagesNeverLeakProviderDetail() {
        for (AuthDataSourceException.Reason reason : AuthDataSourceException.Reason.values()) {
            String message = AuthFailureReasons.messageFor(reason);
            assertFalse(message.isEmpty());
            assertFalse(message.toLowerCase(java.util.Locale.ROOT).contains("firebase"));
            assertFalse(message.contains("ERROR_"));
        }
    }
}
