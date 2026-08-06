-- Records which source message produced which public event.
--
-- Ingestion runs on a schedule and re-reads its mailbox, so the same
-- announcement arrives repeatedly. The unique constraint on the source identity
-- is what makes that harmless: a second submission of the same message can only
-- resolve to the event it already created, never to a duplicate listing.

create table if not exists ingested_events (
    source_system varchar(64) not null,
    source_record_id varchar(512) not null,
    event_id uuid not null,
    content_hash varchar(64),
    ingested_at timestamp with time zone not null,
    primary key (source_system, source_record_id),
    constraint ingested_events_event_fk foreign key (event_id) references events (id)
);

create index if not exists idx_ingested_events_event on ingested_events (event_id);
