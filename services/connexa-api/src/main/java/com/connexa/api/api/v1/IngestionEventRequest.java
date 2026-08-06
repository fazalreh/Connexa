package com.connexa.api.api.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * One announcement-derived event submitted by an approved reader.
 *
 * <p>{@code sourceSystem} and {@code sourceRecordId} identify the originating message and
 * are what make a repeated submission resolve to the event it already created.
 */
public record IngestionEventRequest(
        @NotBlank @Size(max = 64) String sourceSystem,
        @NotBlank @Size(max = 512) String sourceRecordId,
        @Size(max = 128) String contentHash,
        @NotBlank @Size(min = 3, max = 160) String title,
        @NotBlank @Size(min = 3, max = 500) String summary,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 64) String timeZone,
        @NotBlank @Size(max = 200) String venueName,
        @NotBlank @Size(max = 80) String category,
        @Size(max = 200) String organizerName,
        @Positive Integer totalCapacity) {
}
