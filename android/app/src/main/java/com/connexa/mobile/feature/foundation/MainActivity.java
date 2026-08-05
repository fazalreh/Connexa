package com.connexa.mobile.feature.foundation;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.databinding.ActivityMainBinding;
import com.connexa.mobile.feature.assistant.AssistantActivity;
import com.connexa.mobile.feature.auth.SignInActivity;
import com.connexa.mobile.feature.calendar.CalendarActivity;
import com.connexa.mobile.feature.events.EventFeedActivity;
import com.connexa.mobile.feature.notifications.NotificationInboxActivity;
import com.connexa.mobile.feature.organizer.OrganizerDashboardActivity;

public final class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.openEventsButton.setOnClickListener(
                view -> startActivity(new Intent(this, EventFeedActivity.class)));
        binding.openCalendarButton.setOnClickListener(
                view -> startActivity(new Intent(this, CalendarActivity.class)));
        binding.openAccountButton.setOnClickListener(
                view -> startActivity(SignInActivity.newIntent(this)));
        binding.openNotificationsButton.setOnClickListener(
                view -> startActivity(new Intent(this, NotificationInboxActivity.class)));
        binding.openAssistantButton.setOnClickListener(
                view -> startActivity(AssistantActivity.newIntent(this)));
        binding.openOrganizerButton.setOnClickListener(
                view -> startActivity(OrganizerDashboardActivity.newIntent(this)));
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
