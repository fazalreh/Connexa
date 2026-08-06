package com.connexa.mobile.feature.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.R;
import com.connexa.mobile.core.auth.AccountRole;
import com.connexa.mobile.core.auth.AuthDataSource;
import com.connexa.mobile.core.auth.AuthDataSourceException;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.auth.SignUpRequest;
import com.connexa.mobile.databinding.ActivityConnexaSignUpBinding;
import com.connexa.mobile.feature.events.EventFeedActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Collects registration details, validates them, and registers through the configured provider.
 *
 * <p>The requested account role is a stated preference only. Whether an account may act as an
 * organiser is decided by the service from the verified token, never by this screen.
 */
public final class SignUpActivity extends AppCompatActivity {

    private ActivityConnexaSignUpBinding binding;
    private AuthDataSource authDataSource;
    private ExecutorService backgroundExecutor;
    private Handler mainThreadHandler;

    public static Intent newIntent(Context context) {
        return new Intent(context, SignUpActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConnexaSignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authDataSource = ConnexaIdentity.authDataSource(this);
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        binding.signUpToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.createConnexaAccountButton.setOnClickListener(view -> validateForm());
        binding.returnToSignInButton.setOnClickListener(view -> returnToSignIn());

        InputErrorClearing.clearErrorsWhileTyping(
                binding.signUpFullNameInput, binding.signUpFullNameLayout);
        InputErrorClearing.clearErrorsWhileTyping(
                binding.signUpEmailInput, binding.signUpEmailLayout);
        InputErrorClearing.clearErrorsWhileTyping(
                binding.signUpPhoneInput, binding.signUpPhoneLayout);
        // Editing either half of the pair makes a mismatch verdict stale, and that verdict
        // is displayed on the confirmation field.
        InputErrorClearing.clearErrorsWhileTyping(
                binding.signUpPasswordInput,
                binding.signUpPasswordLayout,
                binding.signUpPasswordConfirmationLayout);
        InputErrorClearing.clearErrorsWhileTyping(
                binding.signUpPasswordConfirmationInput,
                binding.signUpPasswordConfirmationLayout);
    }

    @Override
    protected void onDestroy() {
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
            backgroundExecutor = null;
        }
        mainThreadHandler = null;
        binding = null;
        super.onDestroy();
    }

    /** Registers off the main thread; every callback re-checks that the screen still exists. */
    private void submit(SignUpRequest request) {
        setFormEnabled(false);
        binding.signUpStatus.setText(R.string.sign_up_in_progress);
        binding.signUpStatus.setVisibility(View.VISIBLE);
        backgroundExecutor.execute(() -> {
            try {
                authDataSource.signUp(request);
                postToMain(() -> {
                    startActivity(EventFeedActivity.newIntent(this));
                    finish();
                });
            } catch (AuthDataSourceException exception) {
                String message = exception.getMessage();
                postToMain(() -> {
                    setFormEnabled(true);
                    binding.signUpStatus.setText(message);
                    binding.signUpStatus.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void postToMain(Runnable action) {
        Handler handler = mainThreadHandler;
        if (handler != null) {
            handler.post(() -> {
                if (binding != null) {
                    action.run();
                }
            });
        }
    }

    private void setFormEnabled(boolean enabled) {
        binding.createConnexaAccountButton.setEnabled(enabled);
        binding.signUpFullNameInput.setEnabled(enabled);
        binding.signUpEmailInput.setEnabled(enabled);
        binding.signUpPhoneInput.setEnabled(enabled);
        binding.signUpPasswordInput.setEnabled(enabled);
        binding.signUpPasswordConfirmationInput.setEnabled(enabled);
        binding.returnToSignInButton.setEnabled(enabled);
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

        submit(new SignUpRequest(
                textOf(binding.signUpFullNameInput),
                textOf(binding.signUpEmailInput),
                textOf(binding.signUpPhoneInput),
                textOf(binding.signUpPasswordInput),
                selectedAccountRole(),
                binding.signUpTermsCheckbox.isChecked()));
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
