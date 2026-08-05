package com.connexa.mobile.feature.auth;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable field-level feedback returned by {@link AuthFormValidator}. */
public final class AuthValidationResult {

    private final Map<AuthField, String> errors;

    AuthValidationResult(EnumMap<AuthField, String> errors) {
        this.errors = Collections.unmodifiableMap(new EnumMap<>(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** Returns the error for a field, or {@code null} when the field is valid. */
    public String errorFor(AuthField field) {
        return errors.get(Objects.requireNonNull(field, "field"));
    }

    /** Returns the first invalid field in form order, or {@code null} when the form is valid. */
    public AuthField firstInvalidField() {
        for (AuthField field : AuthField.values()) {
            if (errors.containsKey(field)) {
                return field;
            }
        }
        return null;
    }

    public Map<AuthField, String> getErrors() {
        return errors;
    }
}
