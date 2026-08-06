package com.connexa.mobile.core.checkin;

import java.time.Instant;
import java.util.Objects;

/**
 * A signed door pass and the moment it stops being accepted.
 *
 * @param expiresAt the screen refreshes before this, because a pass that lapses while the
 *     attendee is queuing gets them turned away at the front of the line
 */
public record IssuedPass(String pass, Instant expiresAt) {

    public IssuedPass {
        Objects.requireNonNull(pass, "pass is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (pass.isBlank()) {
            throw new IllegalArgumentException("pass is required");
        }
    }
}
