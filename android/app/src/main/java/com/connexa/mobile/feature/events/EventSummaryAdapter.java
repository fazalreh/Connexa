package com.connexa.mobile.feature.events;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
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
        /**
         * @param sharedTitle the title view the opening screen should grow from, so the
         *     transition is anchored to the card that was actually tapped
         */
        void onEventSelected(EventSummary event, android.view.View sharedTitle);
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
            bindBanner(event);
            binding.eventCategory.setText(event.getCategory());
            binding.eventTitle.setText(event.getTitle());
            binding.eventSummary.setText(event.getSummary());
            binding.eventTime.setText(EventTimeFormatter.formatStart(event, Locale.getDefault()));
            binding.eventVenue.setText(event.getVenueName());
            binding.eventCard.setContentDescription(
                    event.getTitle() + ", " + EventTimeFormatter.formatStart(event, Locale.getDefault()));
            // Named per event: a list shows many cards at once, and the framework needs to
            // know which one the new screen grew out of.
            binding.eventTitle.setTransitionName(
                    SharedEventTransition.titleName(event.getId()));
            binding.eventCard.setOnClickListener(
                    view -> listener.onEventSelected(event, binding.eventTitle));
        }

        /**
         * Puts something behind the category on every card.
         *
         * <p>The generated colour is applied first and stays as the backdrop while a cover
         * loads, so a row never flashes empty and a slow image degrades into the design
         * rather than into a hole.
         */
        private void bindBanner(EventSummary event) {
            int[] gradient = EventBannerPalette.gradientFor(event.getId());
            binding.eventCover.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, gradient));

            String cover = event.getCoverImageUrl();
            if (cover == null) {
                // Clear rather than leave whatever the recycled row held, or a scrolling
                // list shows one event's picture on another's card.
                Glide.with(binding.eventCover).clear(binding.eventCover);
                binding.eventCover.setImageDrawable(null);
                return;
            }
            Glide.with(binding.eventCover)
                    .load(cover)
                    .centerCrop()
                    .into(binding.eventCover);
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
