package com.connexa.api.api.v1;

import com.connexa.api.infrastructure.checkin.CheckInResult;
import java.time.Instant;

/**
 * @param alreadyCheckedIn true when this guest had already been scanned, so the steward
 *     sees a distinct outcome rather than an identical success on a duplicate scan
 */
public record CheckInResultResponse(Instant arrivedAt, boolean alreadyCheckedIn) {

    public static CheckInResultResponse from(CheckInResult result) {
        return new CheckInResultResponse(result.arrivedAt(), !result.firstTime());
    }
}
