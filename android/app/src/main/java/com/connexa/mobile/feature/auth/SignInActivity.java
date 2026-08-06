package com.connexa.mobile.feature.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.R;
import com.connexa.mobile.core.auth.AuthDataSource;
import com.connexa.mobile.core.auth.AuthDataSourceException;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.auth.SignInRequest;
import com.connexa.mobile.core.push.PushEnrolment;
import com.connexa.mobile.databinding.ActivityConnexaSignInBinding;
import com.connexa.mobile.feature.events.EventFeedActivity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Collects credentials, validates them, and signs in through the configured provider.
 *
 * <p>This activity owns no provider SDK: it depends on {@link AuthDataSource}, so a build
 * without identity configuration reports the operation as unavailable rather than failing.
 */
public final class SignInActivity extends AppCompatActivity {

    private ActivityConnexaSignInBinding binding;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private AuthDataSource authDataSource;
    private ExecutorService backgroundExecutor;
    private Handler mainThreadHandler;

    public static Intent newIntent(Context context) {
        return new Intent(context, SignInActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConnexaSignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authDataSource = ConnexaIdentity.authDataSource(this);
        // Registered unconditionally: a launcher must exist before the activity resumes,
        // and whether it is used is decided later.
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        binding.signInToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.signInButton.setOnClickListener(view -> validateForm());
        binding.createAccountButton.setOnClickListener(view -> openSignUp());

        InputErrorClearing.clearErrorsWhileTyping(
                binding.signInEmailInput, binding.signInEmailLayout);
        InputErrorClearing.clearErrorsWhileTyping(
                binding.signInPasswordInput, binding.signInPasswordLayout);
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

    /**
     * Runs the provider call off the main thread, then reports back on it.
     *
     * <p>Every callback re-checks {@code binding}: the activity can be destroyed while the
     * request is in flight, and touching views afterwards would crash.
     */
    private void submit(SignInRequest request) {
        setFormEnabled(false);
        showStatus(R.string.sign_in_in_progress);
        backgroundExecutor.execute(() -> {
            try {
                authDataSource.signIn(request);
                // The device is enrolled only now, when a session exists to attach it to.
                PushEnrolment.registerCurrentDevice(this, backgroundExecutor);
                postToMain(() -> {
                    PushEnrolment.requestPermissionIfNeeded(this, notificationPermissionLauncher);
                    startActivity(EventFeedActivity.newIntent(this));
                    finish();
                });
            } catch (AuthDataSourceException exception) {
                String message = exception.getMessage();
                postToMain(() -> {
                    setFormEnabled(true);
                    showStatusText(message);
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
        binding.signInButton.setEnabled(enabled);
        binding.signInEmailInput.setEnabled(enabled);
        binding.signInPasswordInput.setEnabled(enabled);
        binding.createAccountButton.setEnabled(enabled);
    }

    private void showStatus(int messageResource) {
        binding.signInStatus.setText(messageResource);
        binding.signInStatus.setVisibility(View.VISIBLE);
    }

    private void showStatusText(String message) {
        binding.signInStatus.setText(message);
        binding.signInStatus.setVisibility(View.VISIBLE);
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

        submit(new SignInRequest(
                textOf(binding.signInEmailInput),
                textOf(binding.signInPasswordInput)));
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
