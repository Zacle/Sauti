ALTER TABLE agents
    ADD COLUMN default_booking_duration_minutes INT NOT NULL DEFAULT 60;
