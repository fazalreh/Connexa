package com.connexa.mobile.core.auth;

/**
 * The account capabilities selected during registration.
 *
 * <p>Authorization is enforced by the service. This value only communicates the requested
 * account role during registration.</p>
 */
public enum AccountRole {
    ATTENDEE,
    ORGANIZER
}
