package com.connexa.mobile.feature.events;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.R;
import com.connexa.mobile.core.events.EventSummary;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.network.EventApiClient;
import com.connexa.mobile.feature.common.SkeletonPulse;
import com.connexa.mobile.databinding.ActivityEventFeedBinding;
import com.connexa.mobile.feature.navigation.ConnexaBottomNavigation;
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
    private RecommendationAdapter recommendationAdapter;
    private EventApiClient eventApiClient;
    private Handler mainThreadHandler;
    private android.animation.ObjectAnimator skeletonPulse;

    /**
     * Entry point used after sign-in. The task is cleared so the back gesture cannot return
     * to the credential form once the user is signed in.
     */
    public static Intent newIntent(Context context) {
        return new Intent(context, EventFeedActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventFeedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ConnexaBottomNavigation.attach(this, binding.bottomNavigation.getRoot());

        eventAdapter = new EventSummaryAdapter(this::openEvent);
        binding.eventList.setLayoutManager(new LinearLayoutManager(this));
        binding.eventList.setAdapter(eventAdapter);
        binding.eventsToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());

        recommendationAdapter = new RecommendationAdapter(
                event -> startActivity(EventDetailsActivity.newIntent(this, event.getId())));

        binding.recommendationsList.setAdapter(recommendationAdapter);

        backgroundExecutor = Executors.newSingleThreadExecutor();
        Handler mainThreadHandler = new Handler(Looper.getMainLooper());
        eventApiClient = new EventApiClient(new ApiEndpointResolver(BuildConfig.API_BASE_URL));
        presenter = new EventListPresenter(
                eventApiClient,
                backgroundExecutor,
                mainThreadHandler::post,
                this);
        this.mainThreadHandler = mainThreadHandler;

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
        loadRecommendations();
    }

    /**
     * Opens an event, growing the new screen out of the card that was tapped.
     *
     * <p>The title is the shared element rather than the whole card. It is the one thing
     * present and legible on both screens, so the eye can follow it across; animating a card
     * into a full page means morphing a shape into something it has no relationship to.
     */
    private void openEvent(EventSummary event, View sharedTitle) {
        Intent intent = EventDetailsActivity.newIntent(this, event.getId());
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, sharedTitle, SharedEventTransition.titleName(event.getId()));
        startActivity(intent, options.toBundle());
    }

    /**
     * Fills the personalised row, or leaves it hidden.
     *
     * <p>Silence is the correct outcome for a signed-out attendee or one with no history:
     * an empty "Suggested for you" heading is worse than no heading at all.
     */
    private void loadRecommendations() {
        backgroundExecutor.execute(() -> {
            List<EventSummary> suggested = eventApiClient.recommendationsFor(
                    10, ConnexaIdentity.tokenProvider(this));
            mainThreadHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                recommendationAdapter.submit(suggested);
                binding.recommendationsSection.setVisibility(
                        suggested.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    @Override
    public void showLoading() {
        binding.eventList.setVisibility(View.GONE);
        binding.loadMoreEventsButton.setVisibility(View.GONE);
        binding.eventsLoadingState.setVisibility(View.VISIBLE);
        if (skeletonPulse == null) {
            skeletonPulse = SkeletonPulse.start(binding.eventsLoadingState);
        }
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
        stopSkeleton();
        binding.eventsLoadingState.setVisibility(View.GONE);
        binding.eventsEmptyState.setVisibility(View.GONE);
        binding.eventsErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showEmpty() {
        eventAdapter.submitList(Collections.emptyList());
        binding.eventList.setVisibility(View.GONE);
        binding.loadMoreEventsButton.setVisibility(View.GONE);
        stopSkeleton();
        binding.eventsLoadingState.setVisibility(View.GONE);
        binding.eventsEmptyState.setVisibility(View.VISIBLE);
        binding.eventsErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showError(String message) {
        eventAdapter.submitList(Collections.emptyList());
        binding.eventList.setVisibility(View.GONE);
        binding.loadMoreEventsButton.setVisibility(View.GONE);
        stopSkeleton();
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

    /** An animator left running holds the view and redraws a screen nobody is looking at. */
    private void stopSkeleton() {
        SkeletonPulse.stop(skeletonPulse, binding == null ? null : binding.eventsLoadingState);
        skeletonPulse = null;
    }

    @Override
    protected void onDestroy() {
        stopSkeleton();
        if (presenter != null) {
            presenter.cancelPendingWork();
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
        }
        mainThreadHandler = null;
        binding = null;
        super.onDestroy();
    }

    private void loadEvents() {
        presenter.load(binding.searchInput.getText() == null
                ? ""
                : binding.searchInput.getText().toString());
    }
}
