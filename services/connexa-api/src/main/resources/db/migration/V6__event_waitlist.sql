-- Waitlist for events that are full.
--
-- Fairness is the whole point of this table, so position is a monotonic sequence
-- rather than a timestamp: two people joining in the same millisecond must still
-- have a defined order, and wall-clock time can move backwards.
--
-- The sequence is per event, and the unique constraint on (event_id, position)
-- means two concurrent joins cannot claim the same place. One of them loses the
-- insert and retries against a fresh position.

create table if not exists event_waitlist (
    event_id uuid not null,
    identity_issuer varchar(255) not null,
    identity_subject varchar(255) not null,
    position bigint not null,
    joined_at timestamp with time zone not null,
    -- Set when a seat is offered. Until then the entry is simply waiting.
    promoted_at timestamp with time zone,
    primary key (event_id, identity_issuer, identity_subject),
    constraint event_waitlist_event_fk foreign key (event_id) references events (id),
    constraint event_waitlist_position_valid check (position > 0)
);

-- Guarantees a single occupant per place in the queue.
create unique index if not exists idx_waitlist_event_position
    on event_waitlist (event_id, position);

-- The promotion query asks for the lowest-positioned entry not yet promoted.
-- Leading with promoted_at lets that be an index lookup rather than a scan of
-- the whole queue. A partial index would be tighter but is PostgreSQL-only, and
-- the same migrations are applied to an embedded engine in the adapter tests.
create index if not exists idx_waitlist_pending
    on event_waitlist (event_id, promoted_at, position);
