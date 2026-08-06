package com.connexa.api.api.v1;

import com.connexa.api.config.ConnexaProperties;
import com.connexa.api.config.IntegrationAvailability;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformStatusController {

    private final ConnexaProperties properties;
    private final IntegrationAvailability availability;

    public PlatformStatusController(
            ConnexaProperties properties, IntegrationAvailability availability) {
        this.properties = properties;
        this.availability = availability;
    }

    @GetMapping("/status")
    public PlatformStatusResponse status(HttpServletRequest request) {
        return new PlatformStatusResponse(
                properties.serviceName(),
                properties.environment(),
                "available",
                Instant.now(),
                RequestIdFilter.current(request),
                availability.disabledNames());
    }
}
