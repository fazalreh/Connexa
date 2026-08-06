package com.connexa.api.infrastructure.push;

import com.connexa.api.domain.identity.IdentityKey;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Accepts registrations and forgets them, for the non-durable mode.
 *
 * <p>Registration is not refused, because a client registering its device on sign-in should
 * not see an error for a capability the deployment simply does not have. Nothing is
 * delivered because nothing was stored.
 */
@Repository
@ConditionalOnProperty(
        name = "connexa.persistence.mode",
        havingValue = "in-memory",
        matchIfMissing = true)
public class UnavailablePushTokenStore implements PushTokenStore {

    @Override
    public void register(IdentityKey identity, String token, String platform, Instant at) {
        // Nothing durable to register into.
    }

    @Override
    public boolean unregister(String token) {
        return false;
    }

    @Override
    public List<String> tokensFor(IdentityKey identity) {
        return List.of();
    }
}
