ALTER TABLE tenants ADD COLUMN default_save_transcript BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tenants ADD COLUMN default_record_calls BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD COLUMN default_barge_in_sensitivity DOUBLE PRECISION NOT NULL DEFAULT 0.70;
ALTER TABLE tenants ADD COLUMN console_booking_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tenants ADD COLUMN email_booking_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
