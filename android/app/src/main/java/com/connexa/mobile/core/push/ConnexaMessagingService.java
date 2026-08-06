package com.connexa.mobile.core.push;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.connexa.mobile.BuildConfig;
import com.connexa.mobile.R;
import com.connexa.mobile.core.auth.ConnexaIdentity;
import com.connexa.mobile.core.network.ApiEndpointResolver;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Receives the device's messaging token and any notifications sent to it.
 *
 * <p>The token is re-registered whenever the provider rotates it. A token that changed
 * without being re-sent would leave the server delivering to an address that no longer
 * exists, and the user would simply stop receiving notifications with no visible cause.
 */
public final class ConnexaMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "connexa_events";

    /** Registration is network work and must not run on the callback thread. */
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        backgroundExecutor.execute(() -> {
            try {
                new PushRegistrationClient(
                        new ApiEndpointResolver(BuildConfig.API_BASE_URL),
                        ConnexaIdentity.tokenProvider(getApplicationContext()))
                        .register(token);
            } catch (IOException notSignedIn) {
                // Expected before sign-in: there is no session to attach the device to yet.
                // The token is re-offered after sign-in, so nothing is lost.
            }
        });
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        RemoteMessage.Notification notification = message.getNotification();
        if (notification == null) {
            return;
        }
        show(this,
                notification.getTitle(),
                notification.getBody(),
                message.getData().get("eventId"));
    }

    /** Registers the channel. Required before any notification can appear on Android 8+. */
    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_events),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_events_description));
        NotificationManagerCompat.from(context).createNotificationChannel(channel);
    }

    private static void show(Context context, String title, String body, String eventId) {
        ensureChannel(context);
        // On Android 13+ posting without the runtime permission throws; the check keeps a
        // declined permission from crashing a background delivery.
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title == null ? context.getString(R.string.app_name) : title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (body != null) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        }
        // Stable per event, so repeated updates about one event replace rather than stack.
        int id = eventId == null ? 0 : eventId.hashCode();
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    @Override
    public void onDestroy() {
        backgroundExecutor.shutdown();
        super.onDestroy();
    }
}
