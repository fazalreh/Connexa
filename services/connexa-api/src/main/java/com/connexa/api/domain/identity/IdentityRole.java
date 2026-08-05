package com.connexa.api.domain.identity;

/**
 * Roles asserted by a trusted identity provider. Roles are never accepted from
 * request parameters or headers supplied by a client.
 */
public enum IdentityRole {
    ATTENDEE,
    ORGANIZER,
    MODERATOR
}
