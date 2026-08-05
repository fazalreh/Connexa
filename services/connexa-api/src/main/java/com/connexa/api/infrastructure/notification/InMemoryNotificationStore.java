package com.connexa.api.infrastructure.notification;

import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.notification.NotificationItem;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Empty, ephemeral inbox until a trusted notification producer and persistence adapter are connected.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class InMemoryNotificationStore implements NotificationStore {

    private final ConcurrentMap<IdentityKey, ConcurrentMap<UUID, NotificationItem>> inboxes =
            new ConcurrentHashMap<>();

    @Override
    public PageResponse<NotificationItem> findFor(IdentityKey identity, int page, int size) {
        Objects.requireNonNull(identity, "identity is required");
        validatePage(page, size);
        ConcurrentMap<UUID, NotificationItem> inbox = inboxes.get(identity);
        if (inbox == null) {
            return PageResponse.empty(page, size);
        }
        List<NotificationItem> all = inbox.values().stream()
                .sorted(Comparator.comparing(NotificationItem::createdAt).reversed())
                .toList();
        long total = all.size();
        long start = (long) page * size;
        if (start >= total) {
            return new PageResponse<>(List.of(), page, size, total);
        }
        int fromIndex = (int) start;
        int toIndex = Math.min(fromIndex + size, all.size());
        return new PageResponse<>(all.subList(fromIndex, toIndex), page, size, total);
    }

    @Override
    public Optional<NotificationItem> markRead(IdentityKey identity, UUID notificationId, Instant readAt) {
        Objects.requireNonNull(identity, "identity is required");
        Objects.requireNonNull(notificationId, "notificationId is required");
        Objects.requireNonNull(readAt, "readAt is required");
        ConcurrentMap<UUID, NotificationItem> inbox = inboxes.get(identity);
        if (inbox == null) {
            return Optional.empty();
        }
        NotificationItem updated = inbox.computeIfPresent(
                notificationId,
                (ignored, item) -> item.markRead(readAt));
        return Optional.ofNullable(updated);
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
