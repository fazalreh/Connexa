package com.connexa.api.config;

import com.connexa.api.infrastructure.push.FirebasePushSender;
import com.connexa.api.infrastructure.push.PushSender;
import com.connexa.api.infrastructure.push.PushTokenStore;
import com.google.firebase.FirebaseApp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires push delivery onto the Firebase app already created for identity.
 *
 * <p>Present only when that app exists, so a deployment without identity configuration keeps
 * the no-op sender and still starts.
 */
@Configuration(proxyBeanMethods = false)
public class PushConfiguration {

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    PushSender firebasePushSender(FirebaseApp connexaFirebaseApp, PushTokenStore tokenStore) {
        return new FirebasePushSender(connexaFirebaseApp, tokenStore);
    }

    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    PushSender noPushSender() {
        return PushSender.NONE;
    }
}
