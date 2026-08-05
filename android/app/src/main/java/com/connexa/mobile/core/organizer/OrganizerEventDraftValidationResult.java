package com.connexa.mobile.core.organizer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable field-level feedback from {@link OrganizerEventDraftValidator}. */
public final class OrganizerEventDraftValidationResult {

    private final Map<OrganizerDraftField, String> errors;

    OrganizerEventDraftValidationResult(EnumMap<OrganizerDraftField, String> errors) {
        this.errors = Collections.unmodifiableMap(new EnumMap<>(
                Objects.requireNonNull(errors, "errors is required")));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** Returns the error for a field, or {@code null} when that field is valid. */
    public String errorFor(OrganizerDraftField field) {
        return errors.get(Objects.requireNonNull(field, "field is required"));
    }

    /** Returns the first invalid field in editor order, or {@code null} for a valid draft. */
    public OrganizerDraftField firstInvalidField() {
        for (OrganizerDraftField field : OrganizerDraftField.values()) {
            if (errors.containsKey(field)) {
                return field;
            }
        }
        return null;
    }

    public Map<OrganizerDraftField, String> getErrors() {
        return errors;
    }
}
