package com.connexa.api.api.v1;

import com.connexa.api.application.CheckInService;
import java.time.Instant;

/**
 * @param pass the exact text to render as a QR code
 * @param expiresAt lets the client refresh the code before it lapses rather than
 *     showing the attendee something a steward will reject
 */
public record CheckInPassResponse(String pass, Instant expiresAt) {

    public static CheckInPassResponse from(CheckInService.IssuedPass issued) {
        return new CheckInPassResponse(issued.pass(), issued.expiresAt());
    }
}
