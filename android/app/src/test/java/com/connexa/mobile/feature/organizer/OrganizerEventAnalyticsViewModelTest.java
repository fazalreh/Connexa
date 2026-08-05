package com.connexa.mobile.feature.organizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.connexa.mobile.core.organizer.OrganizerEventAnalytics;
import java.time.Instant;
import java.util.UUID;
import org.junit.Test;

public class OrganizerEventAnalyticsViewModelTest {

    @Test
    public void calculatesCapacityAndAttendancePercentages() {
        OrganizerEventAnalyticsViewModel viewModel = OrganizerEventAnalyticsViewModel.from(
                new OrganizerEventAnalytics(
                        UUID.randomUUID(),
                        "Design workshop",
                        80,
                        60,
                        35,
                        4,
                        Instant.parse("2026-09-10T11:00:00Z")));

        assertEquals(75, viewModel.getRegistrationPercent());
        assertEquals(58, viewModel.getAttendancePercent());
        assertFalse(viewModel.isCapacityReached());
        assertTrue(viewModel.requiresAttention());
    }

    @Test
    public void returnsZeroAttendanceWhenNoOneHasRegistered() {
        OrganizerEventAnalyticsViewModel viewModel = OrganizerEventAnalyticsViewModel.from(
                new OrganizerEventAnalytics(
                        UUID.randomUUID(),
                        "Planning session",
                        20,
                        0,
                        0,
                        0,
                        Instant.parse("2026-09-10T11:00:00Z")));

        assertEquals(0, viewModel.getRegistrationPercent());
        assertEquals(0, viewModel.getAttendancePercent());
        assertFalse(viewModel.requiresAttention());
    }
}
