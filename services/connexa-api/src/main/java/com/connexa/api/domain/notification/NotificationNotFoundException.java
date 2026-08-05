package com.connexa.api.domain.notification;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotificationNotFoundException(UUID notificationId) {
        super("No notification exists for id " + notificationId);
    }
}
