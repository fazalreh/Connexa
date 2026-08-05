package com.connexa.mobile.feature.organizer;

import com.connexa.mobile.core.organizer.OrganizerEventAnalytics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Creates a consistent dashboard from service-provided organizer analytics. */
public final class OrganizerDashboardFactory {

    private OrganizerDashboardFactory() {
    }

    public static OrganizerDashboardViewModel create(
            String organizerName,
            int draftCount,
            List<OrganizerEventAnalytics> analytics) {
        if (draftCount < 0) {
            throw new IllegalArgumentException("draftCount must be zero or greater");
        }
        List<OrganizerEventAnalytics> snapshots = Objects.requireNonNull(
                analytics, "analytics is required");
        List<OrganizerEventAnalyticsViewModel> eventModels = new ArrayList<>(snapshots.size());
        long totalRegistrations = 0;
        long totalCheckIns = 0;
        long totalWaitlisted = 0;
        int attentionCount = 0;

        for (OrganizerEventAnalytics snapshot : snapshots) {
            OrganizerEventAnalyticsViewModel eventModel = OrganizerEventAnalyticsViewModel.from(
                    Objects.requireNonNull(snapshot, "analytics cannot contain null"));
            eventModels.add(eventModel);
            totalRegistrations += eventModel.getRegisteredCount();
            totalCheckIns += eventModel.getCheckedInCount();
            totalWaitlisted += eventModel.getWaitlistedCount();
            if (eventModel.requiresAttention()) {
                attentionCount++;
            }
        }

        return new OrganizerDashboardViewModel(
                organizerName,
                draftCount,
                totalRegistrations,
                totalCheckIns,
                totalWaitlisted,
                attentionCount,
                eventModels);
    }
}
