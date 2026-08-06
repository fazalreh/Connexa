-- Links a private organizer draft to the public event it produced.
--
-- The link is what makes publishing safe to repeat: a draft that already carries an event
-- id cannot produce a second one. The unique index enforces that at the storage layer, and
-- because SQL treats nulls as distinct it still permits any number of unpublished drafts.

alter table organizer_event_drafts add column published_event_id uuid;

alter table organizer_event_drafts add constraint drafts_published_event_fk
    foreign key (published_event_id) references events (id);

create unique index if not exists idx_drafts_published_event
    on organizer_event_drafts (published_event_id);
