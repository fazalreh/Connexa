package com.connexa.api.domain.event;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public record EventWindow(Instant startsAt, Instant endsAt, ZoneId timeZone) {

    public EventWindow {
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(endsAt, "endsAt is required");
        Objects.requireNonNull(timeZone, "timeZone is required");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
    }
}
