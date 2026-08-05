package com.connexa.mobile.feature.notifications;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.core.network.EventApiClient;
import com.connexa.mobile.core.notifications.DerivedNotificationDataSource;
import com.connexa.mobile.databinding.NotificationInboxScreenBinding;
import com.connexa.mobile.feature.events.EventDetailsActivity;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notification inbox backed by the Connexa event and notification boundaries.
 *
 * <p>This increment renders safe upcoming-event notices derived from the public event catalog.
 * RSVP confirmations and reminders are added through the authenticated notification endpoint.</p>
 */
public final class NotificationInboxActivity extends AppCompatActivity
        implements NotificationInboxPresenter.View {

    private NotificationInboxScreenBinding binding;
    private NotificationInboxPresenter presenter;
    private NotificationInboxAdapter adapter;
    private ExecutorService backgroundExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = NotificationInboxScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new NotificationInboxAdapter(
                eventId -> startActivity(EventDetailsActivity.newIntent(this, eventId)));
        binding.notificationList.setLayoutManager(new LinearLayoutManager(this));
        binding.notificationList.setAdapter(adapter);
        binding.notificationToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());

        backgroundExecutor = Executors.newSingleThreadExecutor();
        Handler mainThreadHandler = new Handler(Looper.getMainLooper());
        presenter = new NotificationInboxPresenter(
                new DerivedNotificationDataSource(
                        new EventApiClient(new ApiEndpointResolver(BuildConfig.API_BASE_URL)),
                        Clock.systemUTC()),
                backgroundExecutor,
                mainThreadHandler::post,
                this);
        binding.notificationRetryAction.setOnClickListener(view -> presenter.load());

        presenter.load();
    }

    @Override
    public void render(NotificationInboxState state) {
        switch (state.getStatus()) {
            case LOADING:
                binding.notificationList.setVisibility(View.GONE);
                binding.notificationLoadingState.setVisibility(View.VISIBLE);
                binding.notificationEmptyState.setVisibility(View.GONE);
                binding.notificationErrorState.setVisibility(View.GONE);
                break;
            case CONTENT:
                adapter.submitNotifications(state.getNotifications(), Clock.systemDefaultZone());
                binding.notificationList.setVisibility(View.VISIBLE);
                binding.notificationLoadingState.setVisibility(View.GONE);
                binding.notificationEmptyState.setVisibility(View.GONE);
                binding.notificationErrorState.setVisibility(View.GONE);
                break;
            case EMPTY:
                binding.notificationList.setVisibility(View.GONE);
                binding.notificationLoadingState.setVisibility(View.GONE);
                binding.notificationEmptyState.setVisibility(View.VISIBLE);
                binding.notificationErrorState.setVisibility(View.GONE);
                break;
            case ERROR:
                binding.notificationList.setVisibility(View.GONE);
                binding.notificationLoadingState.setVisibility(View.GONE);
                binding.notificationEmptyState.setVisibility(View.GONE);
                binding.notificationErrorMessage.setText(state.getErrorMessage());
                binding.notificationErrorState.setVisibility(View.VISIBLE);
                break;
            default:
                throw new IllegalStateException("Unsupported notification state: " + state.getStatus());
        }
    }

    @Override
    protected void onDestroy() {
        if (presenter != null) {
            presenter.cancelPendingWork();
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        binding = null;
        super.onDestroy();
    }
}
