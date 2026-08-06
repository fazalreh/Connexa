-- Semantic index over the event catalogue.
--
-- Vectors are stored as raw float32 bytes rather than a vector column type. The
-- catalogue is small enough that exact ranking in the application beats adding a
-- database extension: an approximate-nearest-neighbour index earns its keep at
-- hundreds of thousands of rows, not hundreds, and the same migrations must also
-- apply to the embedded engine used by the adapter tests.
--
-- content_hash records what was embedded, so a re-index can skip events whose
-- text has not changed and avoid paying for the same call twice.

create table if not exists event_embeddings (
    event_id uuid primary key,
    model varchar(64) not null,
    dimensions integer not null,
    vector bytea not null,
    content_hash varchar(64) not null,
    embedded_at timestamp with time zone not null,
    constraint event_embeddings_event_fk foreign key (event_id) references events (id),
    constraint event_embeddings_dimensions_valid check (dimensions > 0)
);

-- A model change invalidates every stored vector, so re-indexing selects by model.
create index if not exists idx_event_embeddings_model on event_embeddings (model);
