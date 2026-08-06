package com.connexa.mobile.core.auth;

/**
 * Maps identity-provider error codes to provider-independent failure reasons.
 *
 * <p>Kept apart from the data source so the mapping can be tested without a provider, and so
 * feature code never has to recognise a vendor error string.
 *
 * <p>Wrong credentials, an unknown account and a malformed address all collapse to
 * {@link AuthDataSourceException.Reason#INVALID_CREDENTIALS}. Telling a caller which of those
 * it was would confirm whether an address is registered, turning the sign-in form into a way
 * to enumerate accounts.
 */
public final class AuthFailureReasons {

    private AuthFailureReasons() {
    }

    public static AuthDataSourceException.Reason fromErrorCode(String errorCode) {
        if (errorCode == null) {
            return AuthDataSourceException.Reason.UNKNOWN;
        }
        switch (errorCode) {
            case "ERROR_INVALID_EMAIL":
            case "ERROR_WRONG_PASSWORD":
            case "ERROR_USER_NOT_FOUND":
            case "ERROR_INVALID_CREDENTIAL":
            case "ERROR_USER_DISABLED":
                return AuthDataSourceException.Reason.INVALID_CREDENTIALS;
            case "ERROR_EMAIL_ALREADY_IN_USE":
            case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                return AuthDataSourceException.Reason.ACCOUNT_ALREADY_EXISTS;
            case "ERROR_USER_TOKEN_EXPIRED":
            case "ERROR_INVALID_USER_TOKEN":
            case "ERROR_REQUIRES_RECENT_LOGIN":
                return AuthDataSourceException.Reason.SESSION_EXPIRED;
            case "ERROR_TOO_MANY_REQUESTS":
            case "ERROR_OPERATION_NOT_ALLOWED":
            case "ERROR_NETWORK_REQUEST_FAILED":
                return AuthDataSourceException.Reason.TEMPORARILY_UNAVAILABLE;
            default:
                return AuthDataSourceException.Reason.UNKNOWN;
        }
    }

    /**
     * User-facing text for a failure reason. Deliberately free of provider detail: an error
     * string is not the place to disclose how the identity backend is configured.
     */
    public static String messageFor(AuthDataSourceException.Reason reason) {
        switch (reason) {
            case INVALID_CREDENTIALS:
                return "That email or password is incorrect.";
            case ACCOUNT_ALREADY_EXISTS:
                return "An account already exists for that email.";
            case SESSION_EXPIRED:
                return "Your session expired. Please sign in again.";
            case TEMPORARILY_UNAVAILABLE:
                return "Sign-in is temporarily unavailable. Try again shortly.";
            default:
                return "Could not complete that request.";
        }
    }
}
