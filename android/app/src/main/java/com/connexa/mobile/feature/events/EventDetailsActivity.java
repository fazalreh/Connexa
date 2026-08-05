package com.connexa.mobile.feature.events;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.R;
import com.connexa.mobile.core.attendance.AttendanceApiClient;
import com.connexa.mobile.core.attendance.AttendanceApiException;
import com.connexa.mobile.core.attendance.AttendanceDataSource;
import com.connexa.mobile.core.attendance.AttendanceState;
import com.connexa.mobile.core.attendance.RsvpStatus;
import com.connexa.mobile.core.auth.UnavailableIdentityTokenProvider;
import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventSummary;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.core.network.EventApiClient;
import com.connexa.mobile.databinding.ActivityEventDetailsBinding;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Displays one event after requesting the authoritative summary from the API.
 */
public final class EventDetailsActivity extends AppCompatActivity {

    private static final String EXTRA_EVENT_ID = "com.connexa.mobile.extra.EVENT_ID";

    private ActivityEventDetailsBinding binding;
    private EventDataSource eventDataSource;
    private AttendanceDataSource attendanceDataSource;
    private ExecutorService backgroundExecutor;
    private Handler mainThreadHandler;
    private UUID eventId;
    private AttendanceState attendanceState;
    private volatile boolean destroyed;

    public static Intent newIntent(Context context, UUID eventId) {
        Intent intent = new Intent(context, EventDetailsActivity.class);
        intent.putExtra(EXTRA_EVENT_ID, eventId.toString());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.eventDetailsToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.eventDetailsRetryButton.setOnClickListener(view -> loadEvent());

        ApiEndpointResolver endpointResolver = new ApiEndpointResolver(BuildConfig.API_BASE_URL);
        eventDataSource = new EventApiClient(endpointResolver);
        attendanceDataSource = new AttendanceApiClient(
                endpointResolver,
                new UnavailableIdentityTokenProvider());
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());
        binding.eventSaveButton.setOnClickListener(view -> updateAttendance(AttendanceAction.TOGGLE_SAVED));
        binding.eventRsvpGoingButton.setOnClickListener(
                view -> updateAttendance(AttendanceAction.GOING));
        binding.eventRsvpInterestedButton.setOnClickListener(
                view -> updateAttendance(AttendanceAction.INTERESTED));
        binding.eventRsvpDeclinedButton.setOnClickListener(
                view -> updateAttendance(AttendanceAction.DECLINED));
        setAttendanceControlsEnabled(false);

        String rawEventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        try {
            if (rawEventId == null || rawEventId.trim().isEmpty()) {
                throw new IllegalArgumentException("event ID is required");
            }
            eventId = UUID.fromString(rawEventId);
            loadEvent();
        } catch (IllegalArgumentException exception) {
            showError("This event link is not valid.");
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        binding = null;
        super.onDestroy();
    }

    private void loadEvent() {
        if (eventId == null) {
            showError("This event link is not valid.");
            return;
        }
        showLoading();
        backgroundExecutor.execute(() -> {
            try {
                EventSummary event = eventDataSource.getEvent(eventId);
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showEvent(event);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showError("Unable to load event details. Check the connection and try again.");
                    }
                });
            }
        });
    }

    private void showLoading() {
        binding.eventDetailsContent.setVisibility(View.GONE);
        binding.eventDetailsLoadingState.setVisibility(View.VISIBLE);
        binding.eventDetailsErrorState.setVisibility(View.GONE);
    }

    private void showEvent(EventSummary event) {
        binding.eventDetailsCategory.setText(event.getCategory());
        binding.eventDetailsName.setText(event.getTitle());
        binding.eventDetailsSummary.setText(event.getSummary());
        binding.eventDetailsTime.setText(EventTimeFormatter.formatRange(event, Locale.getDefault()));
        binding.eventDetailsVenue.setText(event.getVenueName());
        binding.eventDetailsOrganizer.setText(event.getOrganizerName());
        binding.eventDetailsStatus.setText(event.getStatus());
        binding.eventDetailsContent.setVisibility(View.VISIBLE);
        binding.eventDetailsLoadingState.setVisibility(View.GONE);
        binding.eventDetailsErrorState.setVisibility(View.GONE);
        loadAttendance();
    }

    private void showError(String message) {
        binding.eventDetailsContent.setVisibility(View.GONE);
        binding.eventDetailsLoadingState.setVisibility(View.GONE);
        binding.eventDetailsErrorMessage.setText(message);
        binding.eventDetailsErrorState.setVisibility(View.VISIBLE);
    }

    private void loadAttendance() {
        if (eventId == null) {
            return;
        }
        binding.eventAttendanceStatus.setText(R.string.event_attendance_loading);
        setAttendanceControlsEnabled(false);
        backgroundExecutor.execute(() -> {
            try {
                AttendanceState state = attendanceDataSource.getAttendance(eventId);
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showAttendance(state);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showAttendanceError(exception);
                    }
                });
            }
        });
    }

    private void updateAttendance(AttendanceAction action) {
        if (eventId == null) {
            return;
        }
        AttendanceState currentState = attendanceState;
        binding.eventAttendanceStatus.setText(R.string.event_attendance_loading);
        setAttendanceControlsEnabled(false);
        backgroundExecutor.execute(() -> {
            try {
                AttendanceState updatedState = applyAttendanceAction(action, currentState);
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showAttendance(updatedState);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showAttendanceError(exception);
                    }
                });
            }
        });
    }

    private AttendanceState applyAttendanceAction(
            AttendanceAction action,
            AttendanceState currentState) throws IOException {
        if (action == AttendanceAction.TOGGLE_SAVED) {
            return currentState != null && currentState.isSaved()
                    ? attendanceDataSource.unsaveEvent(eventId)
                    : attendanceDataSource.saveEvent(eventId);
        }
        if (action == AttendanceAction.GOING) {
            return attendanceDataSource.setRsvp(eventId, RsvpStatus.GOING);
        }
        if (action == AttendanceAction.INTERESTED) {
            return attendanceDataSource.setRsvp(eventId, RsvpStatus.INTERESTED);
        }
        return attendanceDataSource.setRsvp(eventId, RsvpStatus.DECLINED);
    }

    private void showAttendance(AttendanceState state) {
        attendanceState = state;
        int saveButtonLabel = state.isSaved() ? R.string.event_saved : R.string.event_save;
        binding.eventSaveButton.setText(saveButtonLabel);
        binding.eventAttendanceStatus.setText(attendanceStatusText(state));
        setAttendanceControlsEnabled(true);
    }

    private void showAttendanceError(Exception exception) {
        attendanceState = null;
        binding.eventSaveButton.setText(R.string.event_save);
        if (exception instanceof AttendanceApiException) {
            binding.eventAttendanceStatus.setText(exception.getMessage());
        } else {
            binding.eventAttendanceStatus.setText(R.string.event_attendance_access_needed);
        }
        setAttendanceControlsEnabled(true);
    }

    private int attendanceStatusText(AttendanceState state) {
        RsvpStatus rsvpStatus = state.getRsvpStatus();
        if (rsvpStatus == RsvpStatus.GOING) {
            return R.string.event_rsvp_going_status;
        }
        if (rsvpStatus == RsvpStatus.INTERESTED) {
            return R.string.event_rsvp_interested_status;
        }
        if (rsvpStatus == RsvpStatus.DECLINED) {
            return R.string.event_rsvp_declined_status;
        }
        return R.string.event_rsvp_none;
    }

    private void setAttendanceControlsEnabled(boolean enabled) {
        binding.eventSaveButton.setEnabled(enabled);
        binding.eventRsvpGoingButton.setEnabled(enabled);
        binding.eventRsvpInterestedButton.setEnabled(enabled);
        binding.eventRsvpDeclinedButton.setEnabled(enabled);
    }

    private enum AttendanceAction {
        TOGGLE_SAVED,
        GOING,
        INTERESTED,
        DECLINED
    }
}
