-- Cover image for an event.
--
-- Nullable, and expected to stay null for most rows: a campus announcement arriving by
-- email carries no artwork, and the feed has to look deliberate without one. The client
-- draws a generated banner when this is absent rather than a grey placeholder.
--
-- Stored as the delivery URL rather than a storage key. The service already verifies that
-- a reported URL is the upload it signed, so what lands here has been checked once and can
-- be served directly without another round trip to the provider.
alter table events add column if not exists cover_image_url varchar(512);
