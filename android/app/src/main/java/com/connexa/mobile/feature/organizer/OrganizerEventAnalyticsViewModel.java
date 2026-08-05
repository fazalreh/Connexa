package com.connexa.mobile.feature.organizer;

import com.connexa.mobile.core.organizer.OrganizerEventAnalytics;
import java.util.Objects;
import java.util.UUID;

/** Display-ready organizer metrics derived from one immutable analytics snapshot. */
public final class OrganizerEventAnalyticsViewModel {

    private final UUID eventId;
    private final String eventTitle;
    private final int capacity;
    private final int registeredCount;
    private final int checkedInCount;
    private final int waitlistedCount;
    private final int registrationPercent;
    private final int attendancePercent;
    private final boolean capacityReached;
    private final boolean requiresAttention;

    private OrganizerEventAnalyticsViewModel(
            UUID eventId,
            String eventTitle,
            int capacity,
            int registeredCount,
            int checkedInCount,
            int waitlistedCount) {
        this.eventId = Objects.requireNonNull(eventId, "eventId is required");
        this.eventTitle = Objects.requireNonNull(eventTitle, "eventTitle is required");
        this.capacity = capacity;
        this.registeredCount = registeredCount;
        this.checkedInCount = checkedInCount;
        this.waitlistedCount = waitlistedCount;
        this.registrationPercent = percentage(registeredCount, capacity);
        this.attendancePercent = percentage(checkedInCount, registeredCount);
        this.capacityReached = registeredCount == capacity;
        this.requiresAttention = capacityReached || waitlistedCount > 0;
    }

    public static OrganizerEventAnalyticsViewModel from(OrganizerEventAnalytics analytics) {
        Objects.requireNonNull(analytics, "analytics is required");
        return new OrganizerEventAnalyticsViewModel(
                analytics.getEventId(),
                analytics.getEventTitle(),
                analytics.getCapacity(),
                analytics.getRegisteredCount(),
                analytics.getCheckedInCount(),
                analytics.getWaitlistedCount());
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRegisteredCount() {
        return registeredCount;
    }

    public int getCheckedInCount() {
        return checkedInCount;
    }

    public int getWaitlistedCount() {
        return waitlistedCount;
    }

    public int getRegistrationPercent() {
        return registrationPercent;
    }

    public int getAttendancePercent() {
        return attendancePercent;
    }

    public boolean isCapacityReached() {
        return capacityReached;
    }

    public boolean requiresAttention() {
        return requiresAttention;
    }

    private static int percentage(int numerator, int denominator) {
        if (denominator == 0) {
            return 0;
        }
        return (int) ((numerator * 100L + (denominator / 2L)) / denominator);
    }
}
