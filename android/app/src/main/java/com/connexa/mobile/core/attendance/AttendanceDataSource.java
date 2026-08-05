package com.connexa.mobile.core.attendance;

import java.io.IOException;
import java.util.UUID;

/** Authenticated source of the current attendee's saved-event and RSVP state. */
public interface AttendanceDataSource {

    AttendanceState getAttendance(UUID eventId) throws IOException;

    AttendanceState saveEvent(UUID eventId) throws IOException;

    AttendanceState unsaveEvent(UUID eventId) throws IOException;

    AttendanceState setRsvp(UUID eventId, RsvpStatus status) throws IOException;

    AttendanceState clearRsvp(UUID eventId) throws IOException;
}
