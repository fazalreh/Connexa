package com.connexa.api.api.v1;

import java.time.Instant;
import java.util.List;

public record PlatformStatusResponse(
        String service,
        String environment,
        String status,
        Instant observedAt,
        String requestId,
        List<String> disabledIntegrations) {
}
