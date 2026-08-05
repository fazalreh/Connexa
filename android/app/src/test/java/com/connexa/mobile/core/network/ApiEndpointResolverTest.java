package com.connexa.mobile.core.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.UUID;
import org.junit.Test;

public class ApiEndpointResolverTest {

    @Test
    public void resolvesPlatformStatusFromBaseUrlWithoutTrailingSlash() {
        ApiEndpointResolver resolver = new ApiEndpointResolver("http://10.0.2.2:8080");

        assertEquals(
                "http://10.0.2.2:8080/api/v1/platform/status",
                resolver.platformStatus().toString());
    }

    @Test
    public void rejectsNonAbsoluteBaseUrls() {
        assertThrows(IllegalArgumentException.class, () -> new ApiEndpointResolver("/api"));
    }

    @Test
    public void resolvesAQueryEncodedEventList() {
        ApiEndpointResolver resolver = new ApiEndpointResolver("http://10.0.2.2:8080/");

        assertEquals(
                "http://10.0.2.2:8080/api/v1/events?page=0&size=20&q=design+systems",
                resolver.events("design systems", 0, 20).toString());
    }

    @Test
    public void resolvesAnEventByOpaqueUuid() {
        ApiEndpointResolver resolver = new ApiEndpointResolver("https://api.example.test/");
        UUID eventId = UUID.fromString("b50ed025-5ea4-4efa-a7ea-35cd91e75570");

        assertEquals(
                "https://api.example.test/api/v1/events/b50ed025-5ea4-4efa-a7ea-35cd91e75570",
                resolver.event(eventId).toString());
    }

    @Test
    public void resolvesTheServerSideAssistantRoute() {
        ApiEndpointResolver resolver = new ApiEndpointResolver("https://api.example.test/");

        assertEquals(
                "https://api.example.test/api/v1/assistant/messages",
                resolver.assistantMessages().toString());
    }

    @Test
    public void resolvesTheAuthenticatedAttendanceRoutesForOneEvent() {
        ApiEndpointResolver resolver = new ApiEndpointResolver("https://api.example.test/");
        UUID eventId = UUID.fromString("b50ed025-5ea4-4efa-a7ea-35cd91e75570");

        assertEquals(
                "https://api.example.test/api/v1/events/b50ed025-5ea4-4efa-a7ea-35cd91e75570/attendance",
                resolver.attendance(eventId).toString());
        assertEquals(
                "https://api.example.test/api/v1/events/b50ed025-5ea4-4efa-a7ea-35cd91e75570/saved",
                resolver.savedEvent(eventId).toString());
        assertEquals(
                "https://api.example.test/api/v1/events/b50ed025-5ea4-4efa-a7ea-35cd91e75570/rsvp",
                resolver.eventRsvp(eventId).toString());
    }
}
