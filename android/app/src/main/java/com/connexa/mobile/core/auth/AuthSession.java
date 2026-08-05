package com.connexa.mobile.core.auth;

import java.util.Objects;

/**
 * The safe, display-oriented identity returned after successful authentication.
 */
public final class AuthSession {

    private final String subjectId;
    private final String email;
    private final String displayName;
    private final AccountRole accountRole;

    public AuthSession(
            String subjectId,
            String email,
            String displayName,
            AccountRole accountRole) {
        this.subjectId = requireText(subjectId, "subjectId");
        this.email = requireText(email, "email");
        this.displayName = requireText(displayName, "displayName");
        this.accountRole = Objects.requireNonNull(accountRole, "accountRole");
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountRole getAccountRole() {
        return accountRole;
    }

    private static String requireText(String value, String fieldName) {
        String checkedValue = Objects.requireNonNull(value, fieldName).trim();
        if (checkedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return checkedValue;
    }
}
