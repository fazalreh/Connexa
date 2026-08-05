package com.connexa.mobile.feature.organizer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.connexa.mobile.databinding.OrganizerDashboardBinding;
import java.util.Collections;

/** Entry point for the organizer workflow. */
public final class OrganizerDashboardActivity extends AppCompatActivity {

    private OrganizerDashboardBinding binding;
    private OrganizerEventAdapter eventAdapter;

    public static Intent newIntent(Context context) {
        return new Intent(context, OrganizerDashboardActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = OrganizerDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        eventAdapter = new OrganizerEventAdapter(event -> {
            // Analytics navigation is enabled once organizer analytics are returned by the service.
        });
        binding.organizerEventList.setLayoutManager(new LinearLayoutManager(this));
        binding.organizerEventList.setAdapter(eventAdapter);
        binding.organizerDashboardToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.organizerCreateEventButton.setOnClickListener(
                view -> startActivity(OrganizerEventEditorActivity.newIntent(this)));
        binding.organizerRefreshButton.setOnClickListener(view -> renderEmptyDashboard());

        renderEmptyDashboard();
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private void renderEmptyDashboard() {
        eventAdapter.submit(Collections.emptyList());
        binding.organizerTotalRegistrations.setText("0");
        binding.organizerTotalCheckIns.setText("0");
        binding.organizerDraftCount.setText("0");
        binding.organizerDashboardEmptyState.setVisibility(android.view.View.VISIBLE);
    }
}
