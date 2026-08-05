package com.connexa.api.config;

import com.connexa.api.infrastructure.identity.IdentityVerifier;
import com.connexa.api.infrastructure.identity.RejectingIdentityVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Installs a fail-closed identity boundary when rejecting mode is selected. A real verifier is
 * enabled only by selecting a different mode and providing a server-side implementation.
 */
@Configuration(proxyBeanMethods = false)
public class IdentityBoundaryConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "connexa.identity.mode",
            havingValue = "rejecting",
            matchIfMissing = true)
    IdentityVerifier rejectingIdentityVerifier() {
        return new RejectingIdentityVerifier();
    }
}
