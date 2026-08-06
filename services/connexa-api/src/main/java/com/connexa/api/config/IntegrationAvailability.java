package com.connexa.api.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reports which integrations are actually active.
 *
 * <p>This used to be four booleans in {@code application.yml}, hard-coded to {@code false} and
 * never connected to anything. The status endpoint therefore announced that Firebase and the
 * assistant were disabled while both were configured and serving traffic — a status endpoint
 * that contradicts the running system is worse than none, because it is believed.
 *
 * <p>Each answer below is derived from the same setting that selects the adapter, so the report
 * cannot drift from behaviour: there is no second place to update.
 */
@Component
public class IntegrationAvailability {

    private final String identityMode;
    private final String assistantMode;
    private final String ingestionToken;

    public IntegrationAvailability(
            @Value("${connexa.identity.mode:rejecting}") String identityMode,
            @Value("${connexa.assistant.mode:disabled}") String assistantMode,
            @Value("${connexa.ingestion.api-token:}") String ingestionToken) {
        this.identityMode = normalize(identityMode);
        this.assistantMode = normalize(assistantMode);
        this.ingestionToken = ingestionToken == null ? "" : ingestionToken.trim();
    }

    /** Verified identities are available, so protected routes can succeed. */
    public boolean isIdentityEnabled() {
        return !"rejecting".equals(identityMode);
    }

    /** A real assistant gateway is wired, rather than the one that refuses. */
    public boolean isAssistantEnabled() {
        return !"disabled".equals(assistantMode);
    }

    /**
     * Announcement submission is possible. The worker that reads the mailbox runs elsewhere;
     * what this service controls is whether it will accept what that worker sends, which is
     * exactly what the token gates.
     */
    public boolean isAnnouncementIngestionEnabled() {
        return !ingestionToken.isEmpty();
    }

    /** Push delivery is built on the Firebase app that identity creates, so it follows identity. */
    public boolean isPushEnabled() {
        return isIdentityEnabled();
    }

    /** Names of the integrations that are not active, for the status endpoint. */
    public List<String> disabledNames() {
        List<String> disabled = new ArrayList<>();
        if (!isIdentityEnabled()) {
            disabled.add("identity");
        }
        if (!isAssistantEnabled()) {
            disabled.add("assistant");
        }
        if (!isAnnouncementIngestionEnabled()) {
            disabled.add("announcement-ingestion");
        }
        if (!isPushEnabled()) {
            disabled.add("push");
        }
        return List.copyOf(disabled);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
