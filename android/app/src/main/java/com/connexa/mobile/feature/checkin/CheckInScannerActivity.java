package com.connexa.mobile.feature.checkin;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.R;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.checkin.CheckInApiClient;
import com.connexa.mobile.core.checkin.CheckInOutcome;
import com.connexa.mobile.core.checkin.RepeatScanGuard;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.databinding.ActivityCheckInScannerBinding;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The steward's scanner.
 *
 * <p>Runs continuously rather than closing on the first read, because a door is a queue: the
 * useful shape is scan, glance at the result, next guest, without a tap in between.
 *
 * <p>The outcome banner distinguishes three things a steward genuinely needs to tell apart —
 * admitted, already arrived, and refused — because "already arrived" is not a failure but does
 * mean the person in front of them is presenting a code someone has used.
 */
public final class CheckInScannerActivity extends AppCompatActivity {

    private static final String EXTRA_EVENT_ID = "com.connexa.mobile.extra.SCAN_EVENT_ID";
    private static final long BANNER_VISIBLE_MILLIS = 4_000L;

    private ActivityCheckInScannerBinding binding;
    private CheckInApiClient checkInApiClient;
    private ExecutorService backgroundExecutor;
    private Handler mainThreadHandler;
    private UUID eventId;
    private final RepeatScanGuard repeatScanGuard = new RepeatScanGuard();
    private ActivityResultLauncher<String> cameraPermission;
    private volatile boolean destroyed;

    public static Intent newIntent(Context context, UUID eventId) {
        return new Intent(context, CheckInScannerActivity.class)
                .putExtra(EXTRA_EVENT_ID, eventId.toString());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckInScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.scannerToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());

        checkInApiClient = new CheckInApiClient(
                new ApiEndpointResolver(BuildConfig.API_BASE_URL),
                ConnexaIdentity.tokenProvider(this));
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());

        // Only QR is decoded. Leaving every format on makes the decoder slower and invites a
        // stray barcode on a lanyard or a bottle to be read as an attempted check-in.
        binding.scannerPreview.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        binding.scannerPreview.setStatusText(getString(R.string.check_in_scan_hint));

        cameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startScanning();
                    } else {
                        showPermissionNeeded();
                    }
                });

        String rawEventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        try {
            eventId = UUID.fromString(rawEventId == null ? "" : rawEventId);
        } catch (IllegalArgumentException invalid) {
            showOutcome(getString(R.string.check_in_invalid_event), false, true);
            return;
        }
        binding.scannerPermissionButton.setOnClickListener(view -> requestCamera());
        requestCamera();
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanning();
            return;
        }
        cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void startScanning() {
        binding.scannerPermissionState.setVisibility(View.GONE);
        binding.scannerPreview.setVisibility(View.VISIBLE);
        binding.scannerPreview.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result == null || destroyed) {
                    return;
                }
                if (repeatScanGuard.shouldHandle(result.getText(), Instant.now())) {
                    redeem(result.getText());
                }
            }
        });
        binding.scannerPreview.resume();
    }

    private void showPermissionNeeded() {
        binding.scannerPreview.setVisibility(View.GONE);
        binding.scannerPermissionState.setVisibility(View.VISIBLE);
    }

    private void redeem(String scanned) {
        backgroundExecutor.execute(() -> {
            try {
                CheckInOutcome outcome = checkInApiClient.redeem(eventId, scanned);
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showOutcome(
                                outcome.alreadyCheckedIn()
                                        ? getString(R.string.check_in_already)
                                        : getString(R.string.check_in_admitted),
                                !outcome.alreadyCheckedIn(),
                                false);
                    }
                });
            } catch (Exception refused) {
                String message = refused.getMessage();
                // The guest can fix a refusal and present again straight away, so the guard
                // must not hold their code in its quiet period.
                repeatScanGuard.reset();
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showOutcome(
                                message == null ? getString(R.string.check_in_refused) : message,
                                false,
                                true);
                    }
                });
            }
        });
    }

    private void showOutcome(String message, boolean admitted, boolean refused) {
        binding.scannerOutcome.setText(message);
        binding.scannerOutcome.setBackgroundResource(refused
                ? R.drawable.bg_scan_refused
                : admitted ? R.drawable.bg_scan_admitted : R.drawable.bg_scan_repeat);
        binding.scannerOutcome.setVisibility(View.VISIBLE);
        mainThreadHandler.removeCallbacks(hideOutcome);
        mainThreadHandler.postDelayed(hideOutcome, BANNER_VISIBLE_MILLIS);
    }

    private final Runnable hideOutcome = () -> {
        if (!destroyed && binding != null) {
            binding.scannerOutcome.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (binding.scannerPreview.getVisibility() == View.VISIBLE) {
            binding.scannerPreview.resume();
        }
    }

    @Override
    protected void onPause() {
        // The camera is a shared resource; holding it while backgrounded denies it to every
        // other application and keeps a lens live on a screen nobody is looking at.
        binding.scannerPreview.pause();
        super.onPause();
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
        binding = null;
        super.onDestroy();
    }
}
