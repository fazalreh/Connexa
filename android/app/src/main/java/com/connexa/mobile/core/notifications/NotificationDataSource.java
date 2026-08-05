package com.connexa.mobile.core.notifications;

import java.io.IOException;
import java.util.List;

/**
 * Read-only boundary for a user's notification inbox.
 *
 * <p>Feature code depends on this interface instead of a transport or storage implementation.</p>
 */
public interface NotificationDataSource {

    /**
     * Returns the notification items visible to the signed-in user.
     */
    List<NotificationItem> listNotifications() throws IOException;
}
