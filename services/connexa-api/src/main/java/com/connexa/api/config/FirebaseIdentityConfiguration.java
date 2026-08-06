package com.connexa.api.config;

import com.connexa.api.infrastructure.identity.FirebaseIdentityVerifier;
import com.connexa.api.infrastructure.identity.IdentityVerifier;
import com.connexa.api.infrastructure.identity.ProviderIdentityMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Installs the provider-backed identity boundary.
 *
 * <p>Selected by an explicit mode, so it never coexists with the fail-closed default in
 * {@link IdentityBoundaryConfiguration}: exactly one {@link IdentityVerifier} is ever
 * defined.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "connexa.identity.mode", havingValue = "firebase")
@EnableConfigurationProperties(FirebaseIdentityProperties.class)
public class FirebaseIdentityConfiguration {

    private static final String APP_NAME = "connexa";

    @Bean
    FirebaseApp connexaFirebaseApp(FirebaseIdentityProperties properties) throws IOException {
        String projectId = require(properties.projectId(), "connexa.identity.firebase.project-id");
        Path credentials = Path.of(
                require(properties.serviceAccountPath(),
                        "connexa.identity.firebase.service-account-path"));
        if (!Files.isReadable(credentials)) {
            throw new IllegalStateException(
                    "Service-account file is not readable: " + credentials.toAbsolutePath());
        }

        FirebaseOptions options;
        try (InputStream stream = Files.newInputStream(credentials)) {
            options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .setProjectId(projectId)
                    .build();
        }

        // A named app avoids clashing with any default instance and keeps this boundary's
        // lifecycle independent of other Firebase usage.
        return FirebaseApp.getApps().stream()
                .filter(app -> APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
    }

    @Bean
    FirebaseAuth connexaFirebaseAuth(FirebaseApp connexaFirebaseApp) {
        return FirebaseAuth.getInstance(connexaFirebaseApp);
    }

    @Bean
    IdentityVerifier firebaseIdentityVerifier(
            FirebaseAuth connexaFirebaseAuth, FirebaseIdentityProperties properties) {
        return new FirebaseIdentityVerifier(
                connexaFirebaseAuth,
                new ProviderIdentityMapper(
                        properties.organizerEmails(), properties.requireVerifiedEmail()),
                properties.projectId(),
                properties.checkRevoked());
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " must be set when connexa.identity.mode=firebase");
        }
        return value;
    }
}
