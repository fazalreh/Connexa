package com.connexa.mobile.core.contract;

import java.util.Objects;

public final class PlatformStatus {

    private final String service;
    private final String status;
    private final String requestId;

    public PlatformStatus(String service, String status, String requestId) {
        this.service = requireText(service, "service");
        this.status = requireText(status, "status");
        this.requestId = requireText(requestId, "requestId");
    }

    public String getService() {
        return service;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestId() {
        return requestId;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
