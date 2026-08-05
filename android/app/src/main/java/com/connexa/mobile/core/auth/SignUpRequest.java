package com.connexa.mobile.core.auth;

import java.util.Locale;
import java.util.Objects;

/**
 * Registration details passed to an approved authentication implementation after UI validation.
 */
public final class SignUpRequest {

    private final String fullName;
    private final String email;
    private final String phoneNumber;
    private final String password;
    private final AccountRole accountRole;
    private final boolean termsAccepted;

    public SignUpRequest(
            String fullName,
            String email,
            String phoneNumber,
            String password,
            AccountRole accountRole,
            boolean termsAccepted) {
        this.fullName = requiredText(fullName, "fullName");
        this.email = requiredText(email, "email").toLowerCase(Locale.ROOT);
        this.phoneNumber = requiredText(phoneNumber, "phoneNumber");
        this.password = Objects.requireNonNull(password, "password");
        this.accountRole = Objects.requireNonNull(accountRole, "accountRole");
        this.termsAccepted = termsAccepted;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public AccountRole getAccountRole() {
        return accountRole;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    private static String requiredText(String value, String fieldName) {
        String checkedValue = Objects.requireNonNull(value, fieldName).trim();
        if (checkedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return checkedValue;
    }
}
