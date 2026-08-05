package com.connexa.api.infrastructure.notification;

import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.notification.NotificationItem;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for notifications belonging to one verified identity.
 */
public interface NotificationStore {

    PageResponse<NotificationItem> findFor(IdentityKey identity, int page, int size);

    Optional<NotificationItem> markRead(IdentityKey identity, UUID notificationId, Instant readAt);
}
