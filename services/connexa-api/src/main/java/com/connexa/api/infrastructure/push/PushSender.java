package com.connexa.api.infrastructure.push;

import com.connexa.api.domain.identity.IdentityKey;

/**
 * Delivers a notification to every device an identity has registered.
 *
 * <p>Failure is never propagated to the caller: a notification is a courtesy layered on top
 * of an action that has already succeeded, so a provider outage must not turn a completed
 * RSVP into an error the user sees.
 */
public interface PushSender {

    void notify(IdentityKey identity, String title, String body, String eventId);

    /** Used when no provider is configured; delivery is simply skipped. */
    PushSender NONE = (identity, title, body, eventId) -> { };
}
