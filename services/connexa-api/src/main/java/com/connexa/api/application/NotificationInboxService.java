package com.connexa.api.application;

import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.notification.NotificationItem;
import com.connexa.api.domain.notification.NotificationNotFoundException;
import com.connexa.api.infrastructure.notification.NotificationStore;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Identity-scoped notification read and acknowledgement workflow.
 */
@Service
public class NotificationInboxService {

    private final NotificationStore notificationStore;

    public NotificationInboxService(NotificationStore notificationStore) {
        this.notificationStore = Objects.requireNonNull(notificationStore, "notificationStore is required");
    }

    public PageResponse<NotificationItem> findAll(VerifiedIdentity identity, int page, int size) {
        return notificationStore.findFor(requireIdentity(identity), page, size);
    }

    public NotificationItem markRead(VerifiedIdentity identity, UUID notificationId) {
        return notificationStore.markRead(requireIdentity(identity), notificationId, Instant.now())
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    private static IdentityKey requireIdentity(VerifiedIdentity identity) {
        return Objects.requireNonNull(identity, "identity is required").key();
    }
}
