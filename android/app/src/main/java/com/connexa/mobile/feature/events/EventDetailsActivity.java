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
import com.connexa.mobile.core.attendance.CapacityApiClient;
import com.connexa.mobile.core.attendance.CapacityStream;
import com.connexa.mobile.core.attendance.EventCapacity;
import com.connexa.mobile.core.attendance.WaitlistPlace;
import com.connexa.mobile.core.attendance.RsvpStatus;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.events.EventDataSource;
import com.connexa.mobile.core.events.EventSummary;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.core.network.EventApiClient;
import com.connexa.mobile.core.auth.CurrentIdentityClient;
import com.connexa.mobile.feature.checkin.CheckInPassActivity;
import com.connexa.mobile.feature.checkin.CheckInScannerActivity;
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
    private CapacityApiClient capacityApiClient;
    private CapacityStream capacityStream;
    private Thread capacityWatcher;
    private WaitlistPlace lastKnownPlace = WaitlistPlace.notWaiting();
    private String eventTitle = "";
    private CurrentIdentityClient currentIdentityClient;
    private SeatAvailabilityView seatAvailability;
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

        // The title arrives from the API, so the first frame has nothing to animate into.
        // Holding the entry until it is set is what makes the card appear to become the
        // page rather than cross-fading into an empty one.
        //
        // Every path out of loading must release this. A postponed entry with no matching
        // start is a screen that never appears at all.
        supportPostponeEnterTransition();

        ApiEndpointResolver endpointResolver = new ApiEndpointResolver(BuildConfig.API_BASE_URL);
        eventDataSource = new EventApiClient(endpointResolver);
        attendanceDataSource = new AttendanceApiClient(
                endpointResolver,
                ConnexaIdentity.tokenProvider(this));
        capacityApiClient = new CapacityApiClient(
                endpointResolver, ConnexaIdentity.tokenProvider(this));
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());
        binding.eventSaveButton.setOnClickListener(view -> updateAttendance(AttendanceAction.TOGGLE_SAVED));
        binding.eventShowPassButton.setOnClickListener(view -> {
            if (eventId != null) {
                startActivity(CheckInPassActivity.newIntent(this, eventId, eventTitle));
            }
        });
        binding.eventScanButton.setOnClickListener(view -> {
            if (eventId != null) {
                startActivity(CheckInScannerActivity.newIntent(this, eventId));
            }
        });
        currentIdentityClient = new CurrentIdentityClient(
                endpointResolver, ConnexaIdentity.tokenProvider(this));
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
        eventTitle = event.getTitle();
        binding.eventDetailsName.setText(event.getTitle());
        binding.eventDetailsName.setTransitionName(SharedEventTransition.titleName(eventId));
        binding.eventDetailsSummary.setText(event.getSummary());
        binding.eventDetailsTime.setText(EventTimeFormatter.formatRange(event, Locale.getDefault()));
        binding.eventDetailsVenue.setText(event.getVenueName());
        binding.eventDetailsOrganizer.setText(event.getOrganizerName());
        binding.eventDetailsStatus.setText(event.getStatus());
        binding.eventDetailsContent.setVisibility(View.VISIBLE);
        binding.eventDetailsLoadingState.setVisibility(View.GONE);
        binding.eventDetailsErrorState.setVisibility(View.GONE);
        loadAttendance();
        revealOrganizerTools();
        supportStartPostponedEnterTransition();
    }

    /**
     * Shows the door scanner to organizers.
     *
     * <p>Deliberately silent on failure: if the role cannot be established the control simply
     * stays hidden, which is the same outcome as not having the role.
     */
    private void revealOrganizerTools() {
        backgroundExecutor.execute(() -> {
            boolean organizer = currentIdentityClient.isOrganizer();
            mainThreadHandler.post(() -> {
                if (!destroyed && binding != null && organizer) {
                    binding.eventScanButton.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void showError(String message) {
        binding.eventDetailsContent.setVisibility(View.GONE);
        binding.eventDetailsLoadingState.setVisibility(View.GONE);
        binding.eventDetailsErrorMessage.setText(message);
        binding.eventDetailsErrorState.setVisibility(View.VISIBLE);
        // Nothing to animate into, but the screen still has to be allowed to appear.
        supportStartPostponedEnterTransition();
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
                // Read on the same pass so the screen never shows attendance and seats
                // from two different moments.
                EventCapacity capacity = readCapacityQuietly();
                WaitlistPlace place = readWaitlistPlaceQuietly();
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showAttendance(state);
                        showSeats(capacity, place);
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

    /** Seat availability is supplementary; a failure must not break the main screen. */
    private EventCapacity readCapacityQuietly() {
        try {
            return capacityApiClient.getCapacity(eventId);
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    private WaitlistPlace readWaitlistPlaceQuietly() {
        try {
            return capacityApiClient.getWaitlistPlace(eventId);
        } catch (IOException | RuntimeException unavailable) {
            return WaitlistPlace.notWaiting();
        }
    }

    private void showSeats(EventCapacity capacity, WaitlistPlace place) {
        lastKnownPlace = place;
        seatAvailability = SeatAvailabilityView.from(
                capacity,
                attendanceState == null ? null : attendanceState.getRsvpStatus(),
                place);

        String seatText = seatAvailability.getSeatText();
        binding.eventSeatStatus.setText(seatText);
        binding.eventSeatStatus.setVisibility(seatText.isEmpty() ? View.GONE : View.VISIBLE);

        switch (seatAvailability.getAction()) {
            case JOIN_WAITLIST -> {
                binding.eventWaitlistButton.setVisibility(View.VISIBLE);
                binding.eventWaitlistButton.setText(R.string.event_waitlist_join);
                binding.eventWaitlistButton.setOnClickListener(view -> changeWaitlist(true));
            }
            case LEAVE_WAITLIST -> {
                binding.eventWaitlistButton.setVisibility(View.VISIBLE);
                binding.eventWaitlistButton.setText(R.string.event_waitlist_leave);
                binding.eventWaitlistButton.setOnClickListener(view -> changeWaitlist(false));
            }
            default -> binding.eventWaitlistButton.setVisibility(View.GONE);
        }

        // Going is pointless when the event is full and the caller holds no seat; the
        // server would refuse it and the user would only learn that after tapping.
        binding.eventRsvpGoingButton.setEnabled(seatAvailability.isRsvpAllowed());
    }

    /**
     * Follows the seat count for as long as the screen is visible.
     *
     * <p>Started in onResume rather than onCreate so a backgrounded screen is not holding a
     * connection open, and stopped in onPause for the same reason.
     *
     * <p>It runs on its own thread rather than the shared executor. Watching is a read that
     * blocks until the server has something to say, which may be never — put that on the
     * queue the screen's other requests use and it occupies the only thread indefinitely,
     * so everything submitted after it waits forever. That is not a hypothetical: it left
     * the RSVP controls permanently disabled, because the request that enables them was
     * queued behind the watch and never ran.
     */
    private void startWatchingSeats() {
        if (eventId == null || capacityStream != null) {
            return;
        }
        CapacityStream stream = new CapacityStream(
                new ApiEndpointResolver(BuildConfig.API_BASE_URL),
                ConnexaIdentity.tokenProvider(this));
        capacityStream = stream;
        Thread watcher = new Thread(() -> stream.watch(eventId, capacity ->
                mainThreadHandler.post(() -> {
                    if (!destroyed) {
                        showSeats(capacity, lastKnownPlace);
                    }
                })), "connexa-capacity-watch");
        // Daemon so a stream still unwinding cannot hold the process alive after the
        // screen is gone.
        watcher.setDaemon(true);
        capacityWatcher = watcher;
        watcher.start();
    }

    private void stopWatchingSeats() {
        CapacityStream stream = capacityStream;
        capacityStream = null;
        capacityWatcher = null;
        if (stream != null) {
            // Closes the socket, which is what unblocks the reader; the thread then ends
            // on its own.
            stream.cancel();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startWatchingSeats();
    }

    @Override
    protected void onPause() {
        stopWatchingSeats();
        super.onPause();
    }

    private void changeWaitlist(boolean joining) {
        binding.eventWaitlistButton.setEnabled(false);
        backgroundExecutor.execute(() -> {
            Integer message;
            try {
                if (joining) {
                    capacityApiClient.joinWaitlist(eventId);
                    message = R.string.event_waitlist_joined;
                } else {
                    capacityApiClient.leaveWaitlist(eventId);
                    message = R.string.event_waitlist_left;
                }
            } catch (IOException | RuntimeException failure) {
                message = R.string.event_waitlist_failed;
            }
            Integer shown = message;
            mainThreadHandler.post(() -> {
                if (destroyed) {
                    return;
                }
                binding.eventWaitlistButton.setEnabled(true);
                binding.eventAttendanceStatus.setText(shown);
                // Re-read rather than assume: a promotion may have granted a seat
                // between the request and this response.
                loadAttendance();
            });
        });
    }

    private void showAttendance(AttendanceState state) {
        attendanceState = state;
        boolean going = state.getRsvpStatus() == RsvpStatus.GOING;
        binding.eventShowPassButton.setVisibility(going ? View.VISIBLE : View.GONE);
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
