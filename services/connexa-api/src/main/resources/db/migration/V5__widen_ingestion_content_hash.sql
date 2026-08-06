-- The reader's content hash carries an algorithm prefix ("ch1_") ahead of the
-- 64-character digest, so 64 is not wide enough to hold one.
alter table ingested_events alter column content_hash type varchar(128);
