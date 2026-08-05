package com.connexa.api.api.v1;

import com.connexa.api.domain.attendance.AttendanceState;
import com.connexa.api.domain.attendance.RsvpStatus;
import java.time.Instant;
import java.util.UUID;

public record AttendanceStateResponse(
        UUID eventId,
        boolean saved,
        RsvpStatus rsvpStatus,
        Instant updatedAt) {

    public static AttendanceStateResponse from(AttendanceState attendanceState) {
        return new AttendanceStateResponse(
                attendanceState.eventId(),
                attendanceState.saved(),
                attendanceState.rsvpStatus(),
                attendanceState.updatedAt());
    }
}
