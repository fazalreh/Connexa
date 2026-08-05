package com.connexa.api.api.v1;

import com.connexa.api.domain.organizer.CreateOrganizerEventCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record OrganizerEventCreateRequest(
        @NotBlank @Size(min = 3, max = 120) String title,
        @NotBlank @Size(min = 20, max = 5_000) String description,
        @NotBlank @Size(min = 2, max = 160) String location,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 64) String timeZone,
        @NotBlank @Size(min = 2, max = 80) String category,
        @Min(1) @Max(100_000) int capacity) {

    public CreateOrganizerEventCommand toCommand() {
        return new CreateOrganizerEventCommand(
                title,
                description,
                location,
                startsAt,
                endsAt,
                timeZone,
                category,
                capacity);
    }
}
