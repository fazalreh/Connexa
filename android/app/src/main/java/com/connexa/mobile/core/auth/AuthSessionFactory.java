package com.connexa.mobile.core.auth;

import java.util.Locale;

/**
 * Builds a display-oriented session from provider account details.
 *
 * <p>{@link AuthSession} requires a non-blank display name, but an identity provider is free
 * to return none — an account created by an administrator usually has no profile name. Rather
 * than fail sign-in over a cosmetic field, a readable name is derived from the address.
 */
public final class AuthSessionFactory {

    private AuthSessionFactory() {
    }

    public static AuthSession create(
            String subjectId, String email, String providerDisplayName, AccountRole accountRole) {
        return new AuthSession(
                subjectId, email, displayNameFor(providerDisplayName, email), accountRole);
    }

    /**
     * Uses the provider's name when it has one, otherwise the local part of the address with
     * separators turned into spaces, so "fazal.rehman@x.com" reads as "Fazal Rehman".
     */
    public static String displayNameFor(String providerDisplayName, String email) {
        if (providerDisplayName != null && !providerDisplayName.trim().isEmpty()) {
            return providerDisplayName.trim();
        }
        String localPart = email == null ? "" : email.trim();
        int at = localPart.indexOf('@');
        if (at >= 0) {
            // Cut even when the address starts with '@': that leaves an empty local part,
            // which correctly falls through to the generic name below. Skipping the cut
            // would title-case the whole malformed address instead.
            localPart = localPart.substring(0, at);
        }
        localPart = localPart.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        if (localPart.isEmpty()) {
            return "Connexa member";
        }
        StringBuilder titled = new StringBuilder(localPart.length());
        for (String word : localPart.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (titled.length() > 0) {
                titled.append(' ');
            }
            titled.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return titled.toString();
    }
}
