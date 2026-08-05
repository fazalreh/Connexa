package com.connexa.mobile.feature.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.R;
import com.connexa.mobile.databinding.ActivityConnexaSignInBinding;

/**
 * Collects and validates credentials before they are handed to an {@code AuthDataSource}.
 *
 * <p>This activity intentionally owns no identity-provider SDK or credential configuration.</p>
 */
public final class SignInActivity extends AppCompatActivity {

    private ActivityConnexaSignInBinding binding;

    public static Intent newIntent(Context context) {
        return new Intent(context, SignInActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConnexaSignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.signInToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.signInButton.setOnClickListener(view -> validateForm());
        binding.createAccountButton.setOnClickListener(view -> openSignUp());
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private void validateForm() {
        AuthValidationResult validation = AuthFormValidator.validateSignIn(
                textOf(binding.signInEmailInput),
                textOf(binding.signInPasswordInput));

        renderInputError(
                binding.signInEmailLayout,
                validation.errorFor(AuthField.EMAIL));
        renderInputError(
                binding.signInPasswordLayout,
                validation.errorFor(AuthField.PASSWORD));

        if (!validation.isValid()) {
            binding.signInStatus.setVisibility(View.GONE);
            focusFirstInvalidField(validation);
            return;
        }

        binding.signInStatus.setText(R.string.account_access_unavailable);
        binding.signInStatus.setVisibility(View.VISIBLE);
    }

    private void openSignUp() {
        Intent signUpIntent = SignUpActivity.newIntent(this);
        if (signUpIntent.resolveActivity(getPackageManager()) == null) {
            binding.signInStatus.setText(R.string.account_registration_unavailable);
            binding.signInStatus.setVisibility(View.VISIBLE);
            return;
        }
        startActivity(signUpIntent);
    }

    private void focusFirstInvalidField(AuthValidationResult validation) {
        AuthField firstInvalidField = validation.firstInvalidField();
        if (firstInvalidField == AuthField.EMAIL) {
            binding.signInEmailInput.requestFocus();
        } else if (firstInvalidField == AuthField.PASSWORD) {
            binding.signInPasswordInput.requestFocus();
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
}
