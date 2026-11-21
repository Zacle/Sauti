-- Telnyx call-control events can now be replayed when the server stopped before handling them, or
-- retried when handling failed. The payload is kept only until the event is handled, then cleared,
-- because it contains phone numbers.
ALTER TABLE telnyx_webhook_events ADD COLUMN payload_json TEXT;
ALTER TABLE telnyx_webhook_events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
-- When the event should next be looked at; null once it is finished (completed, expired, given up).
ALTER TABLE telnyx_webhook_events ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_telnyx_webhook_events_due ON telnyx_webhook_events(next_attempt_at);
