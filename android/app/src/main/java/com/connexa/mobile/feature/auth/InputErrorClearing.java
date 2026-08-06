package com.connexa.mobile.feature.auth;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Clears a field's validation error once the person starts correcting it.
 *
 * <p>Validation runs on submit, so without this the error outlives the mistake: the typo is
 * fixed and "Enter a valid email address." is still on screen, telling someone their correct
 * input is wrong. The error is a verdict on the value that was submitted, and editing the
 * field makes that verdict stale.
 */
final class InputErrorClearing {

    private InputErrorClearing() {
    }

    /**
     * Clears each layout's error the next time {@code input} is edited.
     *
     * <p>More than one layout is accepted because a verdict can be about a pair of fields
     * rather than one: a password mismatch is reported on the confirmation field, but editing
     * either half of the pair is what makes it stale.
     */
    static void clearErrorsWhileTyping(EditText input, TextInputLayout... layouts) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                for (TextInputLayout layout : layouts) {
                    // Checked first because clearing an error that is not set still costs a
                    // layout pass, and this runs on every keystroke.
                    if (layout.getError() != null) {
                        layout.setError(null);
                    }
                }
            }
        });
    }
}
