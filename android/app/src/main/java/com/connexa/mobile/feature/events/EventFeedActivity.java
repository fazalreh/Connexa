package com.connexa.mobile.feature.events;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.R;
import com.connexa.mobile.core.events.EventSummary;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.core.network.EventApiClient;
import com.connexa.mobile.databinding.ActivityEventFeedBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only event discovery backed by the versioned Connexa HTTP API.
 */
public final class EventFeedActivity extends AppCompatActivity implements EventListPresenter.View {

    private ActivityEventFeedBinding binding;
    private EventSummaryAdapter eventAdapter;
    private EventListPresenter presenter;
    private ExecutorService backgroundExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventFeedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        eventAdapter = new EventSummaryAdapter(
                event -> startActivity(EventDetailsActivity.newIntent(this, event.getId())));
        binding.eventList.setLayoutManager(new LinearLayoutManager(this));
        binding.eventList.setAdapter(eventAdapter);
        binding.eventsToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());

        backgroundExecutor = Executors.newSingleThreadExecutor();
        Handler mainThreadHandler = new Handler(Looper.getMainLooper());
        presenter = new EventListPresenter(
                new EventApiClient(new ApiEndpointResolver(BuildConfig.API_BASE_URL)),
                backgroundExecutor,
                mainThreadHandler::post,
                this);

        binding.searchButton.setOnClickListener(view -> loadEvents());
        binding.refreshButton.setOnClickListener(view -> loadEvents());
        binding.retryButton.setOnClickListener(view -> loadEvents());
        binding.loadMoreEventsButton.setOnClickListener(view -> presenter.loadMore());
        binding.searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadEvents();
                return true;
            }
            return false;
        });

        loadEvents();
    }

    @Override
    public void showLoading() {
        binding.eventList.setVisibility(View.GONE);
        binding.loadMoreEventsButton.setVisibility(View.GONE);
        binding.eventsLoadingState.setVisibility(View.VISIBLE);
        binding.eventsEmptyState.setVisibility(View.GONE);
        binding.eventsErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showEvents(List<EventSummary> events, boolean hasMore) {
        eventAdapter.submitList(new ArrayList<>(events));
        binding.eventList.setVisibility(View.VISIBLE);
        binding.loadMoreEventsButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
        binding.loadMoreEventsButton.setEnabled(true);
        binding.loadMoreEventsButton.setText(R.string.events_load_more);
        binding.eventsLoadingState.setVisibility(View.GONE);
        binding.eventsEmptyState.setVisibility(View.GONE);
        binding.eventsErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showEmpty() {
        eventAdapter.submitList(Collections.emptyList());
        binding.eventList.setVisibility(View.GONE);
        binding.loadMoreEventsButton.setVisibility(View.GONE);
        binding.eventsLoadingState.setVisibility(View.GONE);
        binding.eventsEmptyState.setVisibility(View.VISIBLE);
        binding.eventsErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showError(String message) {
        eventAdapter.submitList(Collections.emptyList());
        binding.eventList.setVisibility(View.GONE);
        binding.loadMoreEventsButton.setVisibility(View.GONE);
        binding.eventsLoadingState.setVisibility(View.GONE);
        binding.eventsEmptyState.setVisibility(View.GONE);
        binding.eventsErrorMessage.setText(message);
        binding.eventsErrorState.setVisibility(View.VISIBLE);
    }

    @Override
    public void showLoadingMore() {
        binding.loadMoreEventsButton.setVisibility(View.VISIBLE);
        binding.loadMoreEventsButton.setEnabled(false);
        binding.loadMoreEventsButton.setText(R.string.events_loading_more);
    }

    @Override
    public void showLoadMoreError(String message) {
        binding.loadMoreEventsButton.setVisibility(View.VISIBLE);
        binding.loadMoreEventsButton.setEnabled(true);
        binding.loadMoreEventsButton.setText(R.string.events_load_more_retry);
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

    private void loadEvents() {
        presenter.load(binding.searchInput.getText() == null
                ? ""
                : binding.searchInput.getText().toString());
    }
}
