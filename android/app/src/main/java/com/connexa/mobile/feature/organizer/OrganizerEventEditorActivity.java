package com.connexa.mobile.feature.organizer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.connexa.mobile.R;
import com.connexa.mobile.core.organizer.OrganizerDraftField;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.core.organizer.OrganizerApiClient;
import com.connexa.mobile.core.organizer.OrganizerApiException;
import com.connexa.mobile.core.organizer.OrganizerEventDraft;
import com.connexa.mobile.databinding.OrganizerEventEditorBinding;
import com.google.android.material.snackbar.Snackbar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects an organizer event draft and validates it locally before a protected service request.
 *
 * <p>Selected media stays in the draft UI until the server-side upload workflow is enabled. The
 * screen never uploads content directly to a storage provider.</p>
 */
public final class OrganizerEventEditorActivity extends AppCompatActivity {

    private final List<Uri> selectedMedia = new ArrayList<>();

    private OrganizerEventEditorBinding binding;
    private OrganizerMediaSelectionAdapter mediaAdapter;
    private OrganizerApiClient organizerApiClient;
    private ExecutorService backgroundExecutor;
    private Handler mainThreadHandler;
    private ActivityResultLauncher<String> mediaPicker;
    private Instant startsAt;
    private Instant endsAt;

    public static Intent newIntent(Context context) {
        return new Intent(context, OrganizerEventEditorActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = OrganizerEventEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mediaAdapter = new OrganizerMediaSelectionAdapter(this::removeMedia);
        binding.organizerEventMediaList.setLayoutManager(new LinearLayoutManager(this));
        binding.organizerEventMediaList.setAdapter(mediaAdapter);
        mediaPicker = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(), this::addSelectedMedia);

        binding.organizerEventEditorToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.organizerEventStartButton.setOnClickListener(view -> chooseDateTime(true));
        binding.organizerEventEndButton.setOnClickListener(view -> chooseDateTime(false));
        binding.organizerAddMediaButton.setOnClickListener(view -> mediaPicker.launch("image/*"));
        binding.organizerSaveEventButton.setOnClickListener(view -> validateDraft());
        mediaAdapter.submit(selectedMedia);

        organizerApiClient = new OrganizerApiClient(
                new ApiEndpointResolver(BuildConfig.API_BASE_URL),
                ConnexaIdentity.tokenProvider(this));
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());
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
     * Publishes the validated draft.
     *
     * <p>Callbacks re-check {@code binding} because the screen can be destroyed while the
     * request is still in flight.
     */
    private void publish(OrganizerEventDraft draft) {
        binding.organizerSaveEventButton.setEnabled(false);
        Snackbar.make(binding.getRoot(), R.string.organizer_publishing, Snackbar.LENGTH_SHORT).show();
        backgroundExecutor.execute(() -> {
            try {
                organizerApiClient.createAndPublish(draft);
                postToMain(() -> {
                    Snackbar.make(binding.getRoot(), R.string.organizer_published,
                            Snackbar.LENGTH_LONG).show();
                    binding.organizerSaveEventButton.setEnabled(true);
                    finish();
                });
            } catch (OrganizerApiException failure) {
                String message = failure.getMessage();
                postToMain(() -> {
                    binding.organizerSaveEventButton.setEnabled(true);
                    Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
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

    private void chooseDateTime(boolean choosingStart) {
        Instant current = choosingStart ? startsAt : (endsAt == null ? startsAt : endsAt);
        ZonedDateTime initial = (current == null ? Instant.now() : current)
                .atZone(ZoneId.systemDefault());
        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> chooseTime(
                        choosingStart,
                        LocalDate.of(year, month + 1, dayOfMonth),
                        initial.toLocalTime()),
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth());
        datePicker.show();
    }

    private void chooseTime(boolean choosingStart, LocalDate date, LocalTime initialTime) {
        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    Instant selected = ZonedDateTime.of(
                            date,
                            LocalTime.of(hourOfDay, minute),
                            ZoneId.systemDefault()).toInstant();
                    if (choosingStart) {
                        startsAt = selected;
                        binding.organizerEventStartButton.setText(format(selected));
                    } else {
                        endsAt = selected;
                        binding.organizerEventEndButton.setText(format(selected));
                    }
                },
                initialTime.getHour(),
                initialTime.getMinute(),
                DateFormat.is24HourFormat(this));
        timePicker.show();
    }

    private void validateDraft() {
        OrganizerEventDraft draft = new OrganizerEventDraft(
                textOf(binding.organizerEventTitleInput),
                textOf(binding.organizerEventDescriptionInput),
                textOf(binding.organizerEventLocationInput),
                startsAt,
                endsAt,
                textOf(binding.organizerEventCategoryInput),
                capacityOf());
        OrganizerEventDraftFormViewModel form = OrganizerEventDraftFormViewModel.from(draft);
        renderValidation(form);
        if (!form.canSubmit()) {
            focusFirstInvalidField(form.firstInvalidField());
            return;
        }

        publish(draft);
    }

    private void renderValidation(OrganizerEventDraftFormViewModel form) {
        binding.organizerEventTitleLayout.setError(form.errorFor(OrganizerDraftField.TITLE));
        binding.organizerEventDescriptionLayout.setError(form.errorFor(OrganizerDraftField.DESCRIPTION));
        binding.organizerEventLocationLayout.setError(form.errorFor(OrganizerDraftField.LOCATION));
        binding.organizerEventCategoryLayout.setError(form.errorFor(OrganizerDraftField.CATEGORY));
        binding.organizerEventCapacityLayout.setError(form.errorFor(OrganizerDraftField.CAPACITY));
        String startError = form.errorFor(OrganizerDraftField.STARTS_AT);
        String endError = form.errorFor(OrganizerDraftField.ENDS_AT);
        binding.organizerEventStartButton.setError(startError);
        binding.organizerEventEndButton.setError(endError);
    }

    private void focusFirstInvalidField(OrganizerDraftField field) {
        if (field == OrganizerDraftField.TITLE) {
            binding.organizerEventTitleInput.requestFocus();
        } else if (field == OrganizerDraftField.DESCRIPTION) {
            binding.organizerEventDescriptionInput.requestFocus();
        } else if (field == OrganizerDraftField.LOCATION) {
            binding.organizerEventLocationInput.requestFocus();
        } else if (field == OrganizerDraftField.CATEGORY) {
            binding.organizerEventCategoryInput.requestFocus();
        } else if (field == OrganizerDraftField.CAPACITY) {
            binding.organizerEventCapacityInput.requestFocus();
        } else if (field == OrganizerDraftField.STARTS_AT) {
            binding.organizerEventStartButton.requestFocus();
        } else if (field == OrganizerDraftField.ENDS_AT) {
            binding.organizerEventEndButton.requestFocus();
        }
    }

    private Integer capacityOf() {
        String value = textOf(binding.organizerEventCapacityInput).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void addSelectedMedia(List<Uri> uris) {
        for (Uri uri : uris) {
            if (uri != null && !selectedMedia.contains(uri)) {
                selectedMedia.add(uri);
            }
        }
        mediaAdapter.submit(selectedMedia);
    }

    private void removeMedia(Uri uri) {
        selectedMedia.remove(uri);
        mediaAdapter.submit(selectedMedia);
    }

    private static String textOf(android.widget.TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }

    private static String format(Instant instant) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
}
