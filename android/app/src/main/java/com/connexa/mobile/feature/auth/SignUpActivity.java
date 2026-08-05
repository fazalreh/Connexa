package com.connexa.mobile.feature.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.R;
import com.connexa.mobile.core.auth.AccountRole;
import com.connexa.mobile.databinding.ActivityConnexaSignUpBinding;

/**
 * Collects and validates registration details before they are handed to an {@code AuthDataSource}.
 */
public final class SignUpActivity extends AppCompatActivity {

    private ActivityConnexaSignUpBinding binding;

    public static Intent newIntent(Context context) {
        return new Intent(context, SignUpActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConnexaSignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.signUpToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.createConnexaAccountButton.setOnClickListener(view -> validateForm());
        binding.returnToSignInButton.setOnClickListener(view -> returnToSignIn());
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private void validateForm() {
        AuthValidationResult validation = AuthFormValidator.validateSignUp(
                textOf(binding.signUpFullNameInput),
                textOf(binding.signUpEmailInput),
                textOf(binding.signUpPhoneInput),
                textOf(binding.signUpPasswordInput),
                textOf(binding.signUpPasswordConfirmationInput),
                selectedAccountRole(),
                binding.signUpTermsCheckbox.isChecked());

        renderInputError(
                binding.signUpFullNameLayout,
                validation.errorFor(AuthField.FULL_NAME));
        renderInputError(
                binding.signUpEmailLayout,
                validation.errorFor(AuthField.EMAIL));
        renderInputError(
                binding.signUpPhoneLayout,
                validation.errorFor(AuthField.PHONE_NUMBER));
        renderInputError(
                binding.signUpPasswordLayout,
                validation.errorFor(AuthField.PASSWORD));
        renderInputError(
                binding.signUpPasswordConfirmationLayout,
                validation.errorFor(AuthField.PASSWORD_CONFIRMATION));
        renderSupportingError(
                binding.signUpRoleError,
                validation.errorFor(AuthField.ACCOUNT_ROLE));
        renderSupportingError(
                binding.signUpTermsError,
                validation.errorFor(AuthField.TERMS));

        if (!validation.isValid()) {
            binding.signUpStatus.setVisibility(View.GONE);
            focusFirstInvalidField(validation);
            return;
        }

        binding.signUpStatus.setText(R.string.account_registration_unavailable);
        binding.signUpStatus.setVisibility(View.VISIBLE);
    }

    private AccountRole selectedAccountRole() {
        return binding.signUpOrganizerRadio.isChecked()
                ? AccountRole.ORGANIZER
                : AccountRole.ATTENDEE;
    }

    private void returnToSignIn() {
        if (!isTaskRoot()) {
            finish();
            return;
        }

        Intent signInIntent = SignInActivity.newIntent(this);
        if (signInIntent.resolveActivity(getPackageManager()) == null) {
            binding.signUpStatus.setText(R.string.account_access_unavailable);
            binding.signUpStatus.setVisibility(View.VISIBLE);
            return;
        }
        startActivity(signInIntent);
        finish();
    }

    private void focusFirstInvalidField(AuthValidationResult validation) {
        AuthField firstInvalidField = validation.firstInvalidField();
        if (firstInvalidField == AuthField.FULL_NAME) {
            binding.signUpFullNameInput.requestFocus();
        } else if (firstInvalidField == AuthField.EMAIL) {
            binding.signUpEmailInput.requestFocus();
        } else if (firstInvalidField == AuthField.PHONE_NUMBER) {
            binding.signUpPhoneInput.requestFocus();
        } else if (firstInvalidField == AuthField.PASSWORD) {
            binding.signUpPasswordInput.requestFocus();
        } else if (firstInvalidField == AuthField.PASSWORD_CONFIRMATION) {
            binding.signUpPasswordConfirmationInput.requestFocus();
        } else if (firstInvalidField == AuthField.ACCOUNT_ROLE) {
            binding.signUpRoleGroup.requestFocus();
        } else if (firstInvalidField == AuthField.TERMS) {
            binding.signUpTermsCheckbox.requestFocus();
        }
    }

    private static String textOf(android.widget.TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }

    private static void renderInputError(
            com.google.android.material.textfield.TextInputLayout layout,
            String error) {
        layout.setError(error);
    }

    private static void renderSupportingError(android.widget.TextView view, String error) {
        if (error == null) {
            view.setText("");
            view.setVisibility(View.GONE);
        } else {
            view.setText(error);
            view.setVisibility(View.VISIBLE);
        }
    }
}
