package com.connexa.mobile.feature.organizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.organizer.OrganizerEventAnalytics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class OrganizerDashboardFactoryTest {

    @Test
    public void aggregatesEventMetricsWithoutChangingTheirDisplayOrder() {
        List<OrganizerEventAnalytics> analytics = new ArrayList<>();
        analytics.add(snapshot("Design workshop", 80, 60, 35, 4));
        analytics.add(snapshot("Community meetup", 30, 30, 18, 0));

        OrganizerDashboardViewModel dashboard = OrganizerDashboardFactory.create(
                "Community team", 2, analytics);

        assertEquals("Community team", dashboard.getOrganizerName());
        assertEquals(2, dashboard.getDraftCount());
        assertEquals(2, dashboard.getPublishedEventCount());
        assertEquals(90, dashboard.getTotalRegistrations());
        assertEquals(53, dashboard.getTotalCheckIns());
        assertEquals(4, dashboard.getTotalWaitlisted());
        assertEquals(2, dashboard.getAttentionCount());
        assertEquals("Design workshop", dashboard.getEventAnalytics().get(0).getEventTitle());
        assertTrue(dashboard.getEventAnalytics().get(1).isCapacityReached());
    }

    private static OrganizerEventAnalytics snapshot(
            String title,
            int capacity,
            int registrations,
            int checkIns,
            int waitlist) {
        return new OrganizerEventAnalytics(
                UUID.randomUUID(),
                title,
                capacity,
                registrations,
                checkIns,
                waitlist,
                Instant.parse("2026-09-10T11:00:00Z"));
    }
}
