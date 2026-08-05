package com.connexa.mobile.feature.calendar;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.core.network.EventApiClient;
import com.connexa.mobile.databinding.CalendarScreenBinding;
import com.connexa.mobile.feature.events.EventDetailsActivity;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only calendar presentation for published events exposed by the Connexa API.
 */
public final class CalendarActivity extends AppCompatActivity implements CalendarPresenter.View {

    private CalendarScreenBinding binding;
    private CalendarPresenter presenter;
    private CalendarDayAdapter dayAdapter;
    private CalendarEventAdapter eventAdapter;
    private CalendarEventFormatter formatter;
    private ExecutorService backgroundExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = CalendarScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        formatter = new CalendarEventFormatter();
        dayAdapter = new CalendarDayAdapter(date -> presenter.selectDate(date));
        eventAdapter = new CalendarEventAdapter(
                event -> startActivity(EventDetailsActivity.newIntent(this, event.getId())));
        binding.calendarGrid.setLayoutManager(new GridLayoutManager(this, CalendarMonthGrid.DAYS_PER_WEEK));
        binding.calendarGrid.setAdapter(dayAdapter);
        binding.calendarEventList.setLayoutManager(new LinearLayoutManager(this));
        binding.calendarEventList.setAdapter(eventAdapter);

        backgroundExecutor = Executors.newSingleThreadExecutor();
        Handler mainThreadHandler = new Handler(Looper.getMainLooper());
        presenter = new CalendarPresenter(
                new EventApiClient(new ApiEndpointResolver(BuildConfig.API_BASE_URL)),
                backgroundExecutor,
                mainThreadHandler::post,
                this);

        binding.calendarToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.calendarPreviousMonth.setOnClickListener(view -> presenter.selectPreviousMonth());
        binding.calendarNextMonth.setOnClickListener(view -> presenter.selectNextMonth());
        binding.calendarRetryButton.setOnClickListener(view -> presenter.refresh());

        presenter.load();
    }

    @Override
    public void showLoading() {
        binding.calendarScrollContent.setVisibility(View.VISIBLE);
        binding.calendarLoadingState.setVisibility(View.VISIBLE);
        binding.calendarErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showCalendar(CalendarScreen screen) {
        dayAdapter.submit(screen.getMonthGrid());
        eventAdapter.submit(screen.getSelectedDateEvents());
        binding.calendarMonthTitle.setText(
                formatter.formatMonth(screen.getVisibleMonth(), Locale.getDefault()));
        binding.calendarSelectedDateTitle.setText(
                formatter.formatSelectedDate(screen.getSelectedDate(), Locale.getDefault()));
        binding.calendarEventList.setVisibility(
                screen.getSelectedDateEvents().isEmpty() ? View.GONE : View.VISIBLE);
        binding.calendarEmptyState.setVisibility(
                screen.getSelectedDateEvents().isEmpty() ? View.VISIBLE : View.GONE);
        binding.calendarLoadingState.setVisibility(View.GONE);
        binding.calendarScrollContent.setVisibility(View.VISIBLE);
        binding.calendarErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showError(String message) {
        binding.calendarScrollContent.setVisibility(View.GONE);
        binding.calendarErrorMessage.setText(message);
        binding.calendarErrorState.setVisibility(View.VISIBLE);
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
