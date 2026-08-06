package com.connexa.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the provider-backed identity boundary.
 *
 * <p>Bound only when {@code connexa.identity.mode=firebase}; the fail-closed default mode
 * needs none of it.
 */
@ConfigurationProperties(prefix = "connexa.identity.firebase")
public record FirebaseIdentityProperties(
        String projectId,
        String serviceAccountPath,
        List<String> organizerEmails,
        boolean requireVerifiedEmail,
        boolean checkRevoked) {

    public FirebaseIdentityProperties {
        organizerEmails = organizerEmails == null ? List.of() : List.copyOf(organizerEmails);
    }
}
