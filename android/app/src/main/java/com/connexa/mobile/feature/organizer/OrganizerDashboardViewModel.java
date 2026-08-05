package com.connexa.mobile.feature.organizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable data needed to render an organizer workspace. */
public final class OrganizerDashboardViewModel {

    private final String organizerName;
    private final int draftCount;
    private final long totalRegistrations;
    private final long totalCheckIns;
    private final long totalWaitlisted;
    private final int attentionCount;
    private final List<OrganizerEventAnalyticsViewModel> eventAnalytics;

    OrganizerDashboardViewModel(
            String organizerName,
            int draftCount,
            long totalRegistrations,
            long totalCheckIns,
            long totalWaitlisted,
            int attentionCount,
            List<OrganizerEventAnalyticsViewModel> eventAnalytics) {
        if (organizerName == null || organizerName.trim().isEmpty()) {
            throw new IllegalArgumentException("organizerName is required");
        }
        if (draftCount < 0 || totalRegistrations < 0 || totalCheckIns < 0
                || totalWaitlisted < 0 || attentionCount < 0) {
            throw new IllegalArgumentException("dashboard counts must be zero or greater");
        }
        this.organizerName = organizerName;
        this.draftCount = draftCount;
        this.totalRegistrations = totalRegistrations;
        this.totalCheckIns = totalCheckIns;
        this.totalWaitlisted = totalWaitlisted;
        this.attentionCount = attentionCount;
        this.eventAnalytics = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(eventAnalytics, "eventAnalytics is required")));
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public int getDraftCount() {
        return draftCount;
    }

    public int getPublishedEventCount() {
        return eventAnalytics.size();
    }

    public long getTotalRegistrations() {
        return totalRegistrations;
    }

    public long getTotalCheckIns() {
        return totalCheckIns;
    }

    public long getTotalWaitlisted() {
        return totalWaitlisted;
    }

    public int getAttentionCount() {
        return attentionCount;
    }

    public List<OrganizerEventAnalyticsViewModel> getEventAnalytics() {
        return eventAnalytics;
    }
}
