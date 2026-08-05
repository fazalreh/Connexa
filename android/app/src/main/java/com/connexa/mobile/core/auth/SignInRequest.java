package com.connexa.mobile.core.auth;

import java.util.Locale;
import java.util.Objects;

/**
 * Credentials submitted for an existing account. Passwords must never be logged or persisted.
 */
public final class SignInRequest {

    private final String email;
    private final String password;

    public SignInRequest(String email, String password) {
        this.email = canonicalEmail(email);
        this.password = Objects.requireNonNull(password, "password");
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    private static String canonicalEmail(String value) {
        return Objects.requireNonNull(value, "email").trim().toLowerCase(Locale.ROOT);
    }
}
