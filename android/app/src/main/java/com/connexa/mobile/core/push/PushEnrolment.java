package com.connexa.mobile.core.push;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Enrols the device for notifications once a session exists.
 *
 * <p>Two things must both happen and neither implies the other: the user grants the runtime
 * permission, and the current token reaches the server. The token is fetched explicitly here
 * rather than waiting for {@code onNewToken}, which only fires when a token is *created* or
 * rotated — an app that already had one would otherwise never register it.
 */
public final class PushEnrolment {

    private PushEnrolment() {
    }

    /** True when the notification permission must be asked for on this device. */
    public static boolean needsPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Before Android 13 notifications are allowed unless the user turned them off
            // in settings, and there is no runtime permission to request.
            return false;
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Asks for the notification permission if it is needed.
     *
     * <p>Requested after sign-in rather than at first launch: a prompt that arrives before
     * the user has seen anything worth being notified about is usually declined, and a
     * declined permission cannot be asked for again.
     */
    public static void requestPermissionIfNeeded(
            AppCompatActivity activity, ActivityResultLauncher<String> launcher) {
        if (needsPermission(activity)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Sends this device's current token to the API. Safe to call on every sign-in. */
    public static void registerCurrentDevice(Context context, ExecutorService backgroundExecutor) {
        Context application = context.getApplicationContext();
        if (!ConnexaIdentity.isConfigured(application)) {
            return;
        }
        ConnexaMessagingService.ensureChannel(application);
        backgroundExecutor.execute(() -> {
            try {
                String token = Tasks.await(FirebaseMessaging.getInstance().getToken());
                new PushRegistrationClient(
                        new ApiEndpointResolver(BuildConfig.API_BASE_URL),
                        ConnexaIdentity.tokenProvider(application))
                        .register(token);
            } catch (IOException | ExecutionException | RuntimeException unavailable) {
                // Enrolment is best effort. Failing here must never block a sign-in that
                // has already succeeded, so no failure is surfaced.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
