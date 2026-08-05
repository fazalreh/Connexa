package com.connexa.mobile.feature.events;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.core.events.EventSummary;
import com.connexa.mobile.databinding.ItemEventSummaryBinding;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders immutable event summaries returned by the Connexa API.
 */
public final class EventSummaryAdapter
        extends ListAdapter<EventSummary, EventSummaryAdapter.EventSummaryViewHolder> {

    public interface Listener {
        void onEventSelected(EventSummary event);
    }

    private final Listener listener;

    public EventSummaryAdapter(Listener listener) {
        super(new EventSummaryDiffCallback());
        this.listener = Objects.requireNonNull(listener, "listener is required");
    }

    @NonNull
    @Override
    public EventSummaryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEventSummaryBinding binding = ItemEventSummaryBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false);
        return new EventSummaryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventSummaryViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static final class EventSummaryViewHolder extends RecyclerView.ViewHolder {

        private final ItemEventSummaryBinding binding;

        EventSummaryViewHolder(ItemEventSummaryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(EventSummary event, Listener listener) {
            binding.eventCategory.setText(event.getCategory());
            binding.eventTitle.setText(event.getTitle());
            binding.eventSummary.setText(event.getSummary());
            binding.eventTime.setText(EventTimeFormatter.formatStart(event, Locale.getDefault()));
            binding.eventVenue.setText(event.getVenueName());
            binding.eventCard.setContentDescription(
                    event.getTitle() + ", " + EventTimeFormatter.formatStart(event, Locale.getDefault()));
            binding.eventCard.setOnClickListener(view -> listener.onEventSelected(event));
        }
    }

    private static final class EventSummaryDiffCallback extends DiffUtil.ItemCallback<EventSummary> {

        @Override
        public boolean areItemsTheSame(@NonNull EventSummary oldItem, @NonNull EventSummary newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull EventSummary oldItem, @NonNull EventSummary newItem) {
            return oldItem.equals(newItem);
        }
    }
}
