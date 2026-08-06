-- Seat capacity and reservation counters for events.
--
-- reserved_count is maintained by the attendance adapter as attendees move in and out
-- of a GOING response. The check constraint below is the actual overbooking guarantee:
-- even if application logic were wrong, the database refuses to store a count above
-- the declared capacity. A null total_capacity means the event is unbounded.

alter table events add column total_capacity integer;
alter table events add column reserved_count integer not null default 0;

alter table events add constraint events_total_capacity_valid
    check (total_capacity is null or total_capacity >= 0);

alter table events add constraint events_reserved_count_valid
    check (
        reserved_count >= 0
        and (total_capacity is null or reserved_count <= total_capacity)
    );
