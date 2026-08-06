-- Door check-in.
--
-- Recorded on the attendance row rather than a separate table: an attendee can
-- only be checked in for an event they hold a seat at, so the two facts belong
-- to the same record and cannot drift apart.
--
-- checked_in_at is nullable and set once. Overwriting it on a second scan would
-- lose the true arrival time, so the write is conditional on it being null.

alter table attendance add column checked_in_at timestamp with time zone;

-- Checked-in attendees must have responded GOING; a seat is what is being
-- honoured at the door.
alter table attendance add constraint attendance_check_in_requires_going
    check (checked_in_at is null or rsvp_status = 'GOING');

create index if not exists idx_attendance_checked_in
    on attendance (event_id, checked_in_at);
