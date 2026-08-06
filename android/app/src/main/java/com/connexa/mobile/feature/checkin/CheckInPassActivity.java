package com.connexa.mobile.feature.checkin;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.R;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.checkin.CheckInApiClient;
import com.connexa.mobile.core.checkin.IssuedPass;
import com.connexa.mobile.core.checkin.PassQrCode;
import com.connexa.mobile.core.checkin.PassRefreshSchedule;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.databinding.ActivityCheckInPassBinding;
import com.google.zxing.common.BitMatrix;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The attendee's door pass.
 *
 * <p>Three things here exist because of how this is actually used — held up at a door, often
 * outdoors, usually in a hurry:
 *
 * <ul>
 *   <li>the screen is kept awake, because a display that sleeps mid-queue means unlocking a
 *       phone one-handed while people wait behind;
 *   <li>brightness is forced to full for as long as this screen is showing, since a dimmed
 *       screen is the most common reason a scanner cannot read a code;
 *   <li>the pass renews itself before it lapses, so reaching the front of the queue never
 *       means presenting something already expired.
 * </ul>
 */
public final class CheckInPassActivity extends AppCompatActivity {

    private static final String EXTRA_EVENT_ID = "com.connexa.mobile.extra.CHECK_IN_EVENT_ID";
    private static final String EXTRA_EVENT_TITLE = "com.connexa.mobile.extra.CHECK_IN_EVENT_TITLE";
    private static final int QR_SIZE_PIXELS = 720;

    private ActivityCheckInPassBinding binding;
    private CheckInApiClient checkInApiClient;
    private ExecutorService backgroundExecutor;
    private Handler mainThreadHandler;
    private UUID eventId;
    private Instant expiresAt;
    private float originalBrightness;
    private volatile boolean destroyed;

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            if (destroyed || binding == null || expiresAt == null) {
                return;
            }
            long remaining = PassRefreshSchedule.secondsRemaining(expiresAt, Instant.now());
            binding.checkInExpiry.setText(
                    getString(R.string.check_in_expires_in, remaining / 60, remaining % 60));
            mainThreadHandler.postDelayed(this, 1_000L);
        }
    };

    public static Intent newIntent(Context context, UUID eventId, String eventTitle) {
        return new Intent(context, CheckInPassActivity.class)
                .putExtra(EXTRA_EVENT_ID, eventId.toString())
                .putExtra(EXTRA_EVENT_TITLE, eventTitle);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckInPassBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.checkInToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.checkInRetryButton.setOnClickListener(view -> loadPass());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String title = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        binding.checkInEventTitle.setText(title == null ? "" : title);

        checkInApiClient = new CheckInApiClient(
                new ApiEndpointResolver(BuildConfig.API_BASE_URL),
                ConnexaIdentity.tokenProvider(this));
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        String rawEventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        try {
            eventId = UUID.fromString(rawEventId == null ? "" : rawEventId);
            loadPass();
        } catch (IllegalArgumentException invalid) {
            showError(getString(R.string.check_in_invalid_event));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        originalBrightness = attributes.screenBrightness;
        attributes.screenBrightness = 1.0f;
        getWindow().setAttributes(attributes);
    }

    @Override
    protected void onPause() {
        // Restoring rather than leaving it bright: this window's override would otherwise
        // outlive the reason for it and drain the battery of whoever forgot to close it.
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = originalBrightness;
        getWindow().setAttributes(attributes);
        super.onPause();
    }

    private void loadPass() {
        if (eventId == null) {
            return;
        }
        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                IssuedPass issued = checkInApiClient.issuePass(eventId);
                Bitmap code = render(issued.pass());
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showPass(issued, code);
                    }
                });
            } catch (Exception failure) {
                String message = failure.getMessage();
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showError(message == null
                                ? getString(R.string.check_in_pass_failed)
                                : message);
                    }
                });
            }
        });
    }

    private void showPass(IssuedPass issued, Bitmap code) {
        expiresAt = issued.expiresAt();
        binding.checkInQr.setImageBitmap(code);
        binding.checkInQr.setVisibility(View.VISIBLE);
        binding.checkInInstruction.setVisibility(View.VISIBLE);
        binding.checkInExpiry.setVisibility(View.VISIBLE);
        binding.checkInLoadingState.setVisibility(View.GONE);
        binding.checkInErrorState.setVisibility(View.GONE);

        mainThreadHandler.removeCallbacks(countdownTick);
        mainThreadHandler.post(countdownTick);
        mainThreadHandler.postDelayed(
                this::loadPass, PassRefreshSchedule.millisUntilRefresh(expiresAt, Instant.now()));
    }

    private void showLoading() {
        binding.checkInLoadingState.setVisibility(View.VISIBLE);
        binding.checkInErrorState.setVisibility(View.GONE);
    }

    private void showError(String message) {
        expiresAt = null;
        mainThreadHandler.removeCallbacks(countdownTick);
        binding.checkInQr.setVisibility(View.GONE);
        binding.checkInInstruction.setVisibility(View.GONE);
        binding.checkInExpiry.setVisibility(View.GONE);
        binding.checkInLoadingState.setVisibility(View.GONE);
        binding.checkInErrorMessage.setText(message);
        binding.checkInErrorState.setVisibility(View.VISIBLE);
    }

    /** Drawn as an opaque black-on-white square: a themed code is a code that will not scan. */
    private static Bitmap render(String pass) {
        BitMatrix matrix = PassQrCode.encode(pass, QR_SIZE_PIXELS);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                pixels[row + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (mainThreadHandler != null) {
            mainThreadHandler.removeCallbacksAndMessages(null);
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = null;
        super.onDestroy();
    }
}
