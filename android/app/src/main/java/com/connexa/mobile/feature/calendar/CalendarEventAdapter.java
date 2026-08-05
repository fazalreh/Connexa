package com.connexa.mobile.feature.calendar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.R;
import com.connexa.mobile.core.events.EventSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter for the event cards shown below the selected calendar day.
 */
public final class CalendarEventAdapter
        extends RecyclerView.Adapter<CalendarEventAdapter.CalendarEventViewHolder> {

    public interface OnEventSelectedListener {
        void onEventSelected(EventSummary event);
    }

    private final CalendarEventFormatter formatter;
    private final OnEventSelectedListener listener;
    private List<EventSummary> events = Collections.emptyList();

    public CalendarEventAdapter(OnEventSelectedListener listener) {
        this(new CalendarEventFormatter(), listener);
    }

    CalendarEventAdapter(CalendarEventFormatter formatter, OnEventSelectedListener listener) {
        this.formatter = Objects.requireNonNull(formatter, "formatter is required");
        this.listener = Objects.requireNonNull(listener, "listener is required");
    }

    public void submit(List<EventSummary> events) {
        this.events = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(events, "events are required")));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CalendarEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_event, parent, false);
        return new CalendarEventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarEventViewHolder holder, int position) {
        EventSummary event = events.get(position);
        holder.bind(event, formatter, listener);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static final class CalendarEventViewHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final TextView time;
        private final TextView venue;

        CalendarEventViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.calendar_event_title);
            time = itemView.findViewById(R.id.calendar_event_time);
            venue = itemView.findViewById(R.id.calendar_event_venue);
        }

        void bind(
                EventSummary event,
                CalendarEventFormatter formatter,
                OnEventSelectedListener listener) {
            title.setText(event.getTitle());
            time.setText(formatter.formatTimeRange(event, Locale.getDefault()));
            venue.setText(event.getVenueName());
            itemView.setOnClickListener(view -> listener.onEventSelected(event));
        }
    }
}
