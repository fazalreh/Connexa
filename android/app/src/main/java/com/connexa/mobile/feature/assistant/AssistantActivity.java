package com.connexa.mobile.feature.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.connexa.mobile.core.assistant.AssistantConversation;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.core.assistant.AssistantApiClient;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.connexa.mobile.databinding.AssistantScreenBinding;
import com.connexa.mobile.feature.navigation.ConnexaBottomNavigation;
import com.connexa.mobile.feature.events.EventDetailsActivity;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the event-assistant conversation UI.
 *
 * <p>Conversation processing is intentionally delegated to a server-facing data source. The
 * initial implementation fails closed until secure account access and the configured service
 * adapter are supplied.</p>
 */
public final class AssistantActivity extends AppCompatActivity implements AssistantPresenter.View {

    private AssistantScreenBinding binding;
    private AssistantPresenter presenter;
    private AssistantMessageAdapter messageAdapter;
    private ExecutorService backgroundExecutor;

    public static Intent newIntent(Context context) {
        return new Intent(context, AssistantActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = AssistantScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ConnexaBottomNavigation.attach(this, binding.bottomNavigation.getRoot());

        messageAdapter = new AssistantMessageAdapter(reference -> startActivity(
                EventDetailsActivity.newIntent(this, reference.getEventId())));
        binding.assistantConversationList.setLayoutManager(new LinearLayoutManager(this));
        binding.assistantConversationList.setAdapter(messageAdapter);
        binding.assistantToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed());
        binding.assistantSendButton.setOnClickListener(
                view -> presenter.submit(textOfComposer()));

        backgroundExecutor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        Executor mainThreadExecutor = handler::post;
        presenter = new AssistantPresenter(
                new AssistantApiClient(
                        new ApiEndpointResolver(BuildConfig.API_BASE_URL),
                        ConnexaIdentity.tokenProvider(this)),
                backgroundExecutor,
                mainThreadExecutor,
                this);
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

    @Override
    public void showSending() {
        if (binding == null) {
            return;
        }
        binding.assistantLoadingState.setVisibility(View.VISIBLE);
        binding.assistantErrorState.setVisibility(View.GONE);
    }

    @Override
    public void showConversation(AssistantConversation conversation) {
        if (binding == null) {
            return;
        }
        messageAdapter.submitMessages(conversation.getMessages());
        binding.assistantLoadingState.setVisibility(View.GONE);
        binding.assistantErrorState.setVisibility(View.GONE);
        int lastPosition = messageAdapter.getItemCount() - 1;
        if (lastPosition >= 0) {
            androidx.recyclerview.widget.RecyclerView conversationList =
                    binding.assistantConversationList;
            conversationList.post(() -> conversationList.scrollToPosition(lastPosition));
        }
    }

    @Override
    public void showInputError(String message) {
        if (binding == null) {
            return;
        }
        binding.assistantMessageInput.setError(message);
        binding.assistantMessageInput.requestFocus();
    }

    @Override
    public void showError(String message) {
        if (binding == null) {
            return;
        }
        binding.assistantLoadingState.setVisibility(View.GONE);
        binding.assistantErrorMessage.setText(message);
        binding.assistantErrorState.setVisibility(View.VISIBLE);
    }

    @Override
    public void clearComposer() {
        if (binding != null) {
            binding.assistantMessageInput.setText("");
            binding.assistantMessageInput.setError(null);
        }
    }

    @Override
    public void setComposerEnabled(boolean enabled) {
        if (binding == null) {
            return;
        }
        binding.assistantMessageInput.setEnabled(enabled);
        binding.assistantSendButton.setEnabled(enabled);
    }

    private String textOfComposer() {
        return binding.assistantMessageInput.getText() == null
                ? ""
                : binding.assistantMessageInput.getText().toString();
    }
}
