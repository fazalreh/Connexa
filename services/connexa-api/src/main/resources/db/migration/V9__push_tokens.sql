-- Device registrations for push delivery.
--
-- Keyed by token rather than by identity: one person may carry several devices,
-- and a token can migrate between accounts when a phone is shared or handed on.
-- Storing the identity as a column instead of a key lets a re-registration move
-- the token to whoever is signed in now, rather than delivering their
-- notifications to the previous owner.

create table if not exists push_tokens (
    token varchar(512) primary key,
    identity_issuer varchar(255) not null,
    identity_subject varchar(255) not null,
    platform varchar(32) not null,
    registered_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null
);

create index if not exists idx_push_tokens_identity
    on push_tokens (identity_issuer, identity_subject);
