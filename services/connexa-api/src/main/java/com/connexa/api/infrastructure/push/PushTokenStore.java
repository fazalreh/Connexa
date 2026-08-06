package com.connexa.api.infrastructure.push;

import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.util.List;

/** Persistence boundary for device push registrations. */
public interface PushTokenStore {

    /**
     * Records a device against the signed-in identity.
     *
     * <p>Re-registering an existing token reassigns it. A shared or handed-on phone would
     * otherwise keep delivering one person's notifications to another.
     */
    void register(IdentityKey identity, String token, String platform, Instant at);

    /** Removes a device, on sign-out or when the provider reports it as stale. */
    boolean unregister(String token);

    List<String> tokensFor(IdentityKey identity);
}
