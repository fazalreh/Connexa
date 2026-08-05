package com.connexa.mobile.feature.calendar;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.R;
import com.google.android.material.card.MaterialCardView;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * RecyclerView adapter for a fixed six-week calendar grid.
 */
public final class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

    public interface OnDateSelectedListener {
        void onDateSelected(LocalDate date);
    }

    private final OnDateSelectedListener listener;
    private List<CalendarDayCell> cells = Collections.emptyList();

    public CalendarDayAdapter(OnDateSelectedListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener is required");
    }

    public void submit(CalendarMonthGrid grid) {
        cells = Objects.requireNonNull(grid, "grid is required").getCells();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_day, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        CalendarDayCell cell = cells.get(position);
        if (cell.isBlank()) {
            holder.bindBlank();
            return;
        }

        LocalDate date = cell.getDate().get();
        holder.bindDay(cell, date, listener);
    }

    @Override
    public int getItemCount() {
        return cells.size();
    }

    static final class DayViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView card;
        private final TextView dayNumber;
        private final TextView eventMarker;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.calendar_day_card);
            dayNumber = itemView.findViewById(R.id.calendar_day_number);
            eventMarker = itemView.findViewById(R.id.calendar_day_marker);
        }

        void bindBlank() {
            card.setCardBackgroundColor(Color.TRANSPARENT);
            card.setEnabled(false);
            card.setClickable(false);
            card.setOnClickListener(null);
            card.setContentDescription(null);
            dayNumber.setText("");
            eventMarker.setVisibility(View.INVISIBLE);
        }

        void bindDay(
                CalendarDayCell cell,
                LocalDate date,
                OnDateSelectedListener listener) {
            boolean selected = cell.isSelected();
            int primaryColor = ContextCompat.getColor(itemView.getContext(), R.color.connexa_primary);
            int surfaceColor = ContextCompat.getColor(itemView.getContext(), R.color.connexa_surface_container);
            int onPrimaryColor = ContextCompat.getColor(itemView.getContext(), R.color.connexa_on_primary);
            int onSurfaceColor = ContextCompat.getColor(itemView.getContext(), R.color.connexa_on_surface);

            card.setCardBackgroundColor(selected ? primaryColor : surfaceColor);
            card.setEnabled(true);
            card.setClickable(true);
            card.setOnClickListener(view -> listener.onDateSelected(date));
            dayNumber.setText(String.valueOf(date.getDayOfMonth()));
            dayNumber.setTextColor(selected ? onPrimaryColor : onSurfaceColor);
            eventMarker.setTextColor(selected ? onPrimaryColor : primaryColor);
            eventMarker.setVisibility(cell.getEventCount() > 0 ? View.VISIBLE : View.INVISIBLE);
            String suffix = cell.getEventCount() == 1 ? " event" : " events";
            card.setContentDescription(String.format(
                    Locale.getDefault(),
                    "%s, %d%s",
                    date,
                    cell.getEventCount(),
                    suffix));
        }
    }
}
