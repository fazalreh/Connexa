package com.connexa.mobile.feature.organizer;

import com.connexa.mobile.core.organizer.OrganizerDraftField;
import com.connexa.mobile.core.organizer.OrganizerEventDraft;
import com.connexa.mobile.core.organizer.OrganizerEventDraftValidationResult;
import com.connexa.mobile.core.organizer.OrganizerEventDraftValidator;
import java.util.Objects;

/** Immutable editor state that keeps validation rules outside Android views. */
public final class OrganizerEventDraftFormViewModel {

    private final OrganizerEventDraft draft;
    private final OrganizerEventDraftValidationResult validation;

    private OrganizerEventDraftFormViewModel(
            OrganizerEventDraft draft,
            OrganizerEventDraftValidationResult validation) {
        this.draft = Objects.requireNonNull(draft, "draft is required");
        this.validation = Objects.requireNonNull(validation, "validation is required");
    }

    public static OrganizerEventDraftFormViewModel from(OrganizerEventDraft draft) {
        Objects.requireNonNull(draft, "draft is required");
        return new OrganizerEventDraftFormViewModel(
                draft,
                OrganizerEventDraftValidator.validate(draft));
    }

    public OrganizerEventDraft getDraft() {
        return draft;
    }

    public boolean canSubmit() {
        return validation.isValid();
    }

    public String errorFor(OrganizerDraftField field) {
        return validation.errorFor(field);
    }

    public OrganizerDraftField firstInvalidField() {
        return validation.firstInvalidField();
    }

    public OrganizerEventDraftValidationResult getValidation() {
        return validation;
    }
}
