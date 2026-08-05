package com.connexa.mobile.feature.auth;

import com.connexa.mobile.core.auth.AccountRole;
import java.util.EnumMap;
import java.util.regex.Pattern;

/**
 * Pure Java validation for authentication forms.
 *
 * <p>The service remains the final authority for account policy. These checks provide immediate,
 * accessible feedback without sending incomplete form data.</p>
 */
public final class AuthFormValidator {

    static final int MIN_REGISTRATION_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_NAME_LENGTH = 100;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_CHARACTERS_PATTERN = Pattern.compile(
            "^\\+?[0-9 .()\\-]+$");

    private AuthFormValidator() {
    }

    public static AuthValidationResult validateSignIn(String email, String password) {
        EnumMap<AuthField, String> errors = new EnumMap<>(AuthField.class);
        validateEmail(email, errors);
        if (isBlank(password)) {
            errors.put(AuthField.PASSWORD, "Password is required.");
        } else if (password.length() > MAX_PASSWORD_LENGTH) {
            errors.put(AuthField.PASSWORD, "Password is too long.");
        }
        return new AuthValidationResult(errors);
    }

    public static AuthValidationResult validateSignUp(
            String fullName,
            String email,
            String phoneNumber,
            String password,
            String passwordConfirmation,
            AccountRole accountRole,
            boolean termsAccepted) {
        EnumMap<AuthField, String> errors = new EnumMap<>(AuthField.class);

        validateFullName(fullName, errors);
        validateEmail(email, errors);
        validatePhoneNumber(phoneNumber, errors);
        validateNewPassword(password, errors);

        if (isBlank(passwordConfirmation)) {
            errors.put(AuthField.PASSWORD_CONFIRMATION, "Please confirm your password.");
        } else if (!safeEquals(password, passwordConfirmation)) {
            errors.put(AuthField.PASSWORD_CONFIRMATION, "Passwords do not match.");
        }

        if (accountRole == null) {
            errors.put(AuthField.ACCOUNT_ROLE, "Choose an account type.");
        }
        if (!termsAccepted) {
            errors.put(AuthField.TERMS, "Please accept the terms to continue.");
        }

        return new AuthValidationResult(errors);
    }

    private static void validateFullName(String fullName, EnumMap<AuthField, String> errors) {
        String normalizedName = normalized(fullName);
        if (normalizedName.isEmpty()) {
            errors.put(AuthField.FULL_NAME, "Full name is required.");
        } else if (normalizedName.length() > MAX_NAME_LENGTH) {
            errors.put(AuthField.FULL_NAME, "Full name must be 100 characters or fewer.");
        }
    }

    private static void validateEmail(String email, EnumMap<AuthField, String> errors) {
        String normalizedEmail = normalized(email);
        if (normalizedEmail.isEmpty()) {
            errors.put(AuthField.EMAIL, "Email address is required.");
        } else if (normalizedEmail.length() > MAX_EMAIL_LENGTH
                || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            errors.put(AuthField.EMAIL, "Enter a valid email address.");
        }
    }

    private static void validatePhoneNumber(
            String phoneNumber,
            EnumMap<AuthField, String> errors) {
        String normalizedPhoneNumber = normalized(phoneNumber);
        if (normalizedPhoneNumber.isEmpty()) {
            errors.put(AuthField.PHONE_NUMBER, "Phone number is required.");
            return;
        }

        int digitCount = 0;
        for (int index = 0; index < normalizedPhoneNumber.length(); index++) {
            if (Character.isDigit(normalizedPhoneNumber.charAt(index))) {
                digitCount++;
            }
        }
        if (!PHONE_CHARACTERS_PATTERN.matcher(normalizedPhoneNumber).matches()
                || digitCount < 7
                || digitCount > 15) {
            errors.put(AuthField.PHONE_NUMBER, "Enter a valid phone number.");
        }
    }

    private static void validateNewPassword(String password, EnumMap<AuthField, String> errors) {
        if (isBlank(password)) {
            errors.put(AuthField.PASSWORD, "Create a password.");
        } else if (password.length() < MIN_REGISTRATION_PASSWORD_LENGTH) {
            errors.put(
                    AuthField.PASSWORD,
                    "Password must be at least " + MIN_REGISTRATION_PASSWORD_LENGTH + " characters.");
        } else if (password.length() > MAX_PASSWORD_LENGTH) {
            errors.put(AuthField.PASSWORD, "Password is too long.");
        }
    }

    private static boolean isBlank(String value) {
        return normalized(value).isEmpty();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean safeEquals(String first, String second) {
        return first != null && first.equals(second);
    }
}
