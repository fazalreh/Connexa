package com.connexa.mobile.feature.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.auth.AccountRole;
import org.junit.Test;

public class AuthFormValidatorTest {

    @Test
    public void signInRequiresAnEmailAddressAndPassword() {
        AuthValidationResult result = AuthFormValidator.validateSignIn(" ", "");

        assertEquals("Email address is required.", result.errorFor(AuthField.EMAIL));
        assertEquals("Password is required.", result.errorFor(AuthField.PASSWORD));
        assertEquals(AuthField.EMAIL, result.firstInvalidField());
    }

    @Test
    public void signInAcceptsAnExistingPasswordWithoutApplyingRegistrationRules() {
        AuthValidationResult result = AuthFormValidator.validateSignIn(
                "member@example.com",
                "short");

        assertTrue(result.isValid());
    }

    @Test
    public void signUpReportsEveryMissingRequiredValue() {
        AuthValidationResult result = AuthFormValidator.validateSignUp(
                "",
                "not-an-email",
                "12",
                "short",
                "different",
                null,
                false);

        assertEquals("Full name is required.", result.errorFor(AuthField.FULL_NAME));
        assertEquals("Enter a valid email address.", result.errorFor(AuthField.EMAIL));
        assertEquals("Enter a valid phone number.", result.errorFor(AuthField.PHONE_NUMBER));
        assertEquals(
                "Password must be at least "
                        + AuthFormValidator.MIN_REGISTRATION_PASSWORD_LENGTH
                        + " characters.",
                result.errorFor(AuthField.PASSWORD));
        assertEquals("Passwords do not match.", result.errorFor(AuthField.PASSWORD_CONFIRMATION));
        assertEquals("Choose an account type.", result.errorFor(AuthField.ACCOUNT_ROLE));
        assertEquals("Please accept the terms to continue.", result.errorFor(AuthField.TERMS));
    }

    @Test
    public void signUpAcceptsACompleteAttendeeRegistration() {
        AuthValidationResult result = AuthFormValidator.validateSignUp(
                "Ayesha Khan",
                "ayesha.khan@example.com",
                "+92 300 1234567",
                "a-secure-password",
                "a-secure-password",
                AccountRole.ATTENDEE,
                true);

        assertTrue(result.isValid());
        assertNull(result.firstInvalidField());
    }
}
