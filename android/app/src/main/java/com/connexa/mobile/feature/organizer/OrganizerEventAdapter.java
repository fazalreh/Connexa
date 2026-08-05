package com.connexa.mobile.feature.organizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.R;
import com.connexa.mobile.databinding.ItemOrganizerEventBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Renders organizer-owned event analytics supplied by the organizer service boundary. */
public final class OrganizerEventAdapter
        extends RecyclerView.Adapter<OrganizerEventAdapter.EventViewHolder> {

    public interface Listener {
        void onEventSelected(OrganizerEventAnalyticsViewModel event);
    }

    private final Listener listener;
    private List<OrganizerEventAnalyticsViewModel> events = Collections.emptyList();

    public OrganizerEventAdapter(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener is required");
    }

    public void submit(List<OrganizerEventAnalyticsViewModel> events) {
        this.events = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(events, "events are required")));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOrganizerEventBinding binding = ItemOrganizerEventBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EventViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        holder.bind(events.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static final class EventViewHolder extends RecyclerView.ViewHolder {

        private final ItemOrganizerEventBinding binding;

        EventViewHolder(ItemOrganizerEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(OrganizerEventAnalyticsViewModel event, Listener listener) {
            binding.organizerEventItemTitle.setText(event.getEventTitle());
            binding.organizerEventItemRegistration.setText(itemView.getContext().getString(
                    R.string.organizer_registration_summary,
                    event.getRegisteredCount(),
                    event.getCapacity()));
            binding.organizerEventItemRegistrationProgress.setProgress(
                    event.getRegistrationPercent());
            binding.organizerEventItemAttention.setVisibility(
                    event.requiresAttention() ? View.VISIBLE : View.GONE);
            if (event.requiresAttention()) {
                binding.organizerEventItemAttention.setText(R.string.organizer_needs_attention);
            }
            binding.organizerEventItem.setOnClickListener(view -> listener.onEventSelected(event));
        }
    }
}
