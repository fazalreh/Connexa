package com.connexa.api.infrastructure.push;

import com.connexa.api.domain.identity.IdentityKey;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends notifications through the identity provider's messaging service.
 *
 * <p>Reuses the Firebase app already initialised for identity verification, so no second
 * credential is introduced.
 *
 * <p>A token the provider reports as unregistered is deleted rather than retried. Devices
 * are reinstalled and tokens rotate constantly, so a registry that never prunes grows
 * without bound and wastes a send on every future notification.
 */
public final class FirebasePushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushSender.class);

    private final FirebaseMessaging messaging;
    private final PushTokenStore tokenStore;

    public FirebasePushSender(FirebaseApp firebaseApp, PushTokenStore tokenStore) {
        this.messaging = FirebaseMessaging.getInstance(
                Objects.requireNonNull(firebaseApp, "firebaseApp is required"));
        this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore is required");
    }

    @Override
    public void notify(IdentityKey identity, String title, String body, String eventId) {
        List<String> tokens = tokenStore.tokensFor(identity);
        for (String token : tokens) {
            Message.Builder message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build());
            if (eventId != null) {
                // Carried as data so the app can open the right screen when tapped.
                message.putData("eventId", eventId);
            }
            try {
                messaging.send(message.build());
            } catch (FirebaseMessagingException exception) {
                handleFailure(token, exception);
            }
        }
    }

    private void handleFailure(String token, FirebaseMessagingException exception) {
        MessagingErrorCode code = exception.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
            tokenStore.unregister(token);
            return;
        }
        // A transient provider failure must not surface to the user: the action they
        // performed already succeeded, and the notification is a courtesy on top of it.
        log.warn("Could not deliver a notification", exception);
    }
}
