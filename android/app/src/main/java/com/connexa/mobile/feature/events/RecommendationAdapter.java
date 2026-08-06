package com.connexa.mobile.feature.events;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.core.events.EventSummary;
import com.connexa.mobile.databinding.ItemRecommendationBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Horizontal row of suggested events. */
public final class RecommendationAdapter
        extends RecyclerView.Adapter<RecommendationAdapter.RecommendationHolder> {

    private final List<EventSummary> events = new ArrayList<>();
    private final Consumer<EventSummary> onSelected;

    public RecommendationAdapter(Consumer<EventSummary> onSelected) {
        this.onSelected = Objects.requireNonNull(onSelected, "onSelected is required");
    }

    public void submit(List<EventSummary> updated) {
        events.clear();
        if (updated != null) {
            events.addAll(updated);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecommendationHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RecommendationHolder(ItemRecommendationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecommendationHolder holder, int position) {
        holder.bind(events.get(position));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    final class RecommendationHolder extends RecyclerView.ViewHolder {

        private final ItemRecommendationBinding binding;

        RecommendationHolder(ItemRecommendationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(EventSummary event) {
            binding.recommendationCategory.setText(event.getCategory());
            binding.recommendationTitle.setText(event.getTitle());
            binding.recommendationTime.setText(
                    EventTimeFormatter.formatStart(event, java.util.Locale.getDefault()));
            binding.recommendationCard.setOnClickListener(view -> onSelected.accept(event));
        }
    }
}
