package com.connexa.mobile.core.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ApiEndpointResolverTest {

    @Test
    public void resolvesPlatformStatusFromBaseUrlWithoutTrailingSlash() {
        ApiEndpointResolver resolver = new ApiEndpointResolver("http://10.0.2.2:8080");

        assertEquals(
                "http://10.0.2.2:8080/api/v1/platform/status",
                resolver.platformStatus().toString());
    }

    @Test
    public void rejectsNonAbsoluteBaseUrls() {
        assertThrows(IllegalArgumentException.class, () -> new ApiEndpointResolver("/api"));
    }
}
