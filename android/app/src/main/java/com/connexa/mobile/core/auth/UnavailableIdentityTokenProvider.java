package com.connexa.mobile.core.auth;

import java.io.IOException;

/** Safe default that prevents protected requests without a configured identity session. */
public final class UnavailableIdentityTokenProvider implements IdentityTokenProvider {

    @Override
    public String currentBearerToken() throws IOException {
        throw new IOException("Secure account access is unavailable.");
    }
}
