package com.connexa.mobile.feature.foundation;

import com.connexa.mobile.core.network.ApiEndpointResolver;

public final class FoundationPresenter {

    private final ApiEndpointResolver endpointResolver;

    public FoundationPresenter(ApiEndpointResolver endpointResolver) {
        this.endpointResolver = endpointResolver;
    }

    public String initialStatusText() {
        return "Foundation ready. Local API target: " + endpointResolver.platformStatus();
    }
}
