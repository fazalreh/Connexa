package com.connexa.mobile.core.events;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Android representation of the public event-summary contract.
 */
public final class EventSummary {

    private final UUID id;
    private final String title;
    private final String summary;
    private final Instant startsAt;
    private final Instant endsAt;
    private final String timeZone;
    private final String venueName;
    private final String category;
    private final String organizerName;
    private final String status;
    private final String coverImageUrl;
    private final long revision;
    private final Instant createdAt;
    private final Instant updatedAt;

    public EventSummary(
            UUID id,
            String title,
            String summary,
            Instant startsAt,
            Instant endsAt,
            String timeZone,
            String venueName,
            String category,
            String organizerName,
            String status,
            long revision,
            Instant createdAt,
            Instant updatedAt) {
        this(id, title, summary, startsAt, endsAt, timeZone, venueName, category,
                organizerName, status, revision, createdAt, updatedAt, null);
    }

    /**
     * @param coverImageUrl null for the great majority of events. One arriving from an
     *     announcement email carries no artwork, and the feed draws a generated banner in
     *     its place rather than a gap.
     */
    public EventSummary(
            UUID id,
            String title,
            String summary,
            Instant startsAt,
            Instant endsAt,
            String timeZone,
            String venueName,
            String category,
            String organizerName,
            String status,
            long revision,
            Instant createdAt,
            Instant updatedAt,
            String coverImageUrl) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.title = requireText(title, "title");
        this.summary = requireText(summary, "summary");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt is required");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt is required");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        this.timeZone = requireText(timeZone, "timeZone");
        ZoneId.of(this.timeZone);
        this.venueName = requireText(venueName, "venueName");
        this.category = requireText(category, "category");
        this.organizerName = requireText(organizerName, "organizerName");
        this.status = requireText(status, "status");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be zero or greater");
        }
        this.revision = revision;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        // Blank and absent mean the same thing, so readers have one case rather than two.
        this.coverImageUrl = coverImageUrl == null || coverImageUrl.trim().isEmpty()
                ? null
                : coverImageUrl.trim();
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getCategory() {
        return category;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    /** @return the cover address, or null when the event has none */
    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public String getStatus() {
        return status;
    }

    public long getRevision() {
        return revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventSummary)) {
            return false;
        }
        EventSummary that = (EventSummary) other;
        return revision == that.revision
                && id.equals(that.id)
                && title.equals(that.title)
                && summary.equals(that.summary)
                && startsAt.equals(that.startsAt)
                && endsAt.equals(that.endsAt)
                && timeZone.equals(that.timeZone)
                && venueName.equals(that.venueName)
                && category.equals(that.category)
                && organizerName.equals(that.organizerName)
                && status.equals(that.status)
                && createdAt.equals(that.createdAt)
                && updatedAt.equals(that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                title,
                summary,
                startsAt,
                endsAt,
                timeZone,
                venueName,
                category,
                organizerName,
                status,
                revision,
                createdAt,
                updatedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
