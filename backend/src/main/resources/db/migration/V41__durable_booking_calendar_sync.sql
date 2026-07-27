ALTER TABLE bookings
    ADD COLUMN calendar_sync_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE bookings
    ADD COLUMN calendar_sync_next_attempt_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_bookings_calendar_sync_due
    ON bookings (calendar_sync_status, calendar_sync_next_attempt_at);
