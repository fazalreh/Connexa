package com.connexa.mobile.feature.foundation;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.databinding.ActivityMainBinding;

public final class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FoundationPresenter presenter = new FoundationPresenter(
                new ApiEndpointResolver(BuildConfig.API_BASE_URL));
        binding.foundationStatus.setText(presenter.initialStatusText());
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
