package com.connexa.mobile.feature.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.connexa.mobile.core.notifications.NotificationItem;
import com.connexa.mobile.core.notifications.NotificationType;
import com.connexa.mobile.databinding.ItemNotificationInboxBinding;
import com.connexa.mobile.databinding.ItemNotificationSectionBinding;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Renders recent and earlier notification sections without exposing storage details to the UI.
 */
public final class NotificationInboxAdapter
        extends ListAdapter<NotificationInboxRow, RecyclerView.ViewHolder> {

    public interface Listener {
        void onRelatedEventSelected(UUID eventId);
    }

    private final Listener listener;

    public NotificationInboxAdapter(Listener listener) {
        super(new NotificationInboxDiffCallback());
        this.listener = Objects.requireNonNull(listener, "listener is required");
    }

    /**
     * Groups the supplied items into recent and earlier sections before rendering them.
     */
    public void submitNotifications(List<NotificationItem> notifications, Clock clock) {
        submitList(NotificationInboxRows.from(notifications, clock));
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == NotificationInboxRow.VIEW_TYPE_SECTION) {
            return new SectionViewHolder(ItemNotificationSectionBinding.inflate(inflater, parent, false));
        }
        if (viewType == NotificationInboxRow.VIEW_TYPE_NOTIFICATION) {
            return new NotificationViewHolder(ItemNotificationInboxBinding.inflate(inflater, parent, false));
        }
        throw new IllegalArgumentException("Unsupported notification view type: " + viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        NotificationInboxRow row = getItem(position);
        if (holder instanceof SectionViewHolder && row instanceof NotificationInboxRow.SectionRow) {
            ((SectionViewHolder) holder).bind((NotificationInboxRow.SectionRow) row);
            return;
        }
        if (holder instanceof NotificationViewHolder && row instanceof NotificationInboxRow.NotificationRow) {
            ((NotificationViewHolder) holder).bind(
                    ((NotificationInboxRow.NotificationRow) row).getNotification(), listener);
            return;
        }
        throw new IllegalStateException("Notification row and view holder do not match");
    }

    static final class SectionViewHolder extends RecyclerView.ViewHolder {

        private final ItemNotificationSectionBinding binding;

        SectionViewHolder(ItemNotificationSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NotificationInboxRow.SectionRow row) {
            binding.notificationSectionTitle.setText(row.getTitle());
        }
    }

    static final class NotificationViewHolder extends RecyclerView.ViewHolder {

        private final ItemNotificationInboxBinding binding;

        NotificationViewHolder(ItemNotificationInboxBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(NotificationItem notification, Listener listener) {
            binding.notificationType.setText(typeLabel(notification.getType()));
            binding.notificationTitle.setText(notification.getTitle());
            binding.notificationMessage.setText(notification.getMessage());
            binding.notificationTime.setText(NotificationTimeFormatter.formatRelative(
                    notification.getOccurredAt(), Clock.systemDefaultZone()));
            binding.notificationUnreadIndicator.setVisibility(
                    notification.isRead() ? View.GONE : View.VISIBLE);
            binding.notificationCard.setContentDescription(
                    notification.getTitle() + ", " + notification.getMessage());

            boolean canOpenEvent = notification.hasRelatedEvent();
            binding.notificationCard.setClickable(canOpenEvent);
            binding.notificationCard.setFocusable(canOpenEvent);
            binding.notificationCard.setOnClickListener(canOpenEvent
                    ? view -> listener.onRelatedEventSelected(notification.getRelatedEventId())
                    : null);

            boolean showEventAction = canOpenEvent && notification.getType() != NotificationType.NEW_EVENT;
            binding.notificationEventAction.setVisibility(showEventAction ? View.VISIBLE : View.GONE);
            binding.notificationEventAction.setOnClickListener(showEventAction
                    ? view -> listener.onRelatedEventSelected(notification.getRelatedEventId())
                    : null);
        }
    }

    private static String typeLabel(NotificationType type) {
        switch (type) {
            case RSVP_CONFIRMED:
                return "RSVP confirmed";
            case EVENT_REMINDER:
                return "Event reminder";
            case NEW_EVENT:
                return "New event";
            default:
                throw new IllegalArgumentException("Unsupported notification type: " + type);
        }
    }

    private static final class NotificationInboxDiffCallback
            extends DiffUtil.ItemCallback<NotificationInboxRow> {

        @Override
        public boolean areItemsTheSame(
                @NonNull NotificationInboxRow oldItem,
                @NonNull NotificationInboxRow newItem) {
            if (oldItem.getViewType() != newItem.getViewType()) {
                return false;
            }
            if (oldItem instanceof NotificationInboxRow.SectionRow
                    && newItem instanceof NotificationInboxRow.SectionRow) {
                return ((NotificationInboxRow.SectionRow) oldItem).getTitle().equals(
                        ((NotificationInboxRow.SectionRow) newItem).getTitle());
            }
            if (oldItem instanceof NotificationInboxRow.NotificationRow
                    && newItem instanceof NotificationInboxRow.NotificationRow) {
                return ((NotificationInboxRow.NotificationRow) oldItem).getNotification().getId().equals(
                        ((NotificationInboxRow.NotificationRow) newItem).getNotification().getId());
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(
                @NonNull NotificationInboxRow oldItem,
                @NonNull NotificationInboxRow newItem) {
            if (oldItem instanceof NotificationInboxRow.SectionRow
                    && newItem instanceof NotificationInboxRow.SectionRow) {
                return ((NotificationInboxRow.SectionRow) oldItem).getTitle().equals(
                        ((NotificationInboxRow.SectionRow) newItem).getTitle());
            }
            if (oldItem instanceof NotificationInboxRow.NotificationRow
                    && newItem instanceof NotificationInboxRow.NotificationRow) {
                return ((NotificationInboxRow.NotificationRow) oldItem).getNotification().equals(
                        ((NotificationInboxRow.NotificationRow) newItem).getNotification());
            }
            return false;
        }
    }
}
