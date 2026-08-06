-- Connexa durable persistence baseline.
--
-- Column widths are deliberately wider than the domain limits enforced in Java.
-- Domain records remain the authority on validation; these bounds only stop a
-- malformed write from reaching disk. Text length in Java is measured in code
-- points, so a generous column avoids rejecting input the domain accepts.
--
-- The DDL stays portable so the same file can be applied to PostgreSQL in a
-- deployment and to an embedded engine in tests.

create table if not exists events (
    id uuid primary key,
    title varchar(320) not null,
    summary varchar(1000) not null,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    time_zone varchar(128) not null,
    venue_name varchar(400) not null,
    category varchar(160) not null,
    organizer_name varchar(400) not null,
    status varchar(32) not null,
    revision bigint not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint events_window_valid check (ends_at > starts_at),
    constraint events_revision_valid check (revision >= 0),
    constraint events_updated_after_created check (updated_at >= created_at)
);

-- Public discovery always filters on status and orders by start time.
create index if not exists idx_events_status_starts_at on events (status, starts_at);

create table if not exists attendance (
    identity_issuer varchar(255) not null,
    identity_subject varchar(255) not null,
    event_id uuid not null,
    saved boolean not null default false,
    rsvp_status varchar(32),
    updated_at timestamp with time zone,
    primary key (identity_issuer, identity_subject, event_id),
    -- A row with neither a save nor an RSVP carries no timestamp, matching the
    -- domain record which nulls updated_at once all state is cleared.
    constraint attendance_timestamp_present check (
        (saved = false and rsvp_status is null and updated_at is null)
        or ((saved = true or rsvp_status is not null) and updated_at is not null)
    )
);

create index if not exists idx_attendance_identity_updated
    on attendance (identity_issuer, identity_subject, updated_at desc);

create table if not exists organizer_event_drafts (
    id uuid primary key,
    owner_issuer varchar(255) not null,
    owner_subject varchar(255) not null,
    title varchar(240) not null,
    description varchar(10000) not null,
    location varchar(320) not null,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    time_zone varchar(128) not null,
    category varchar(160) not null,
    capacity integer not null,
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint drafts_window_valid check (ends_at > starts_at),
    constraint drafts_capacity_valid check (capacity >= 1 and capacity <= 100000),
    constraint drafts_updated_after_created check (updated_at >= created_at)
);

create index if not exists idx_drafts_owner_updated
    on organizer_event_drafts (owner_issuer, owner_subject, updated_at desc);

create table if not exists notifications (
    id uuid primary key,
    identity_issuer varchar(255) not null,
    identity_subject varchar(255) not null,
    type varchar(32) not null,
    title varchar(320) not null,
    body varchar(4000) not null,
    event_id uuid,
    created_at timestamp with time zone not null,
    read_at timestamp with time zone,
    constraint notifications_read_after_created check (read_at is null or read_at >= created_at)
);

create index if not exists idx_notifications_identity_created
    on notifications (identity_issuer, identity_subject, created_at desc);
