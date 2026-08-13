ALTER TABLE tenants ADD COLUMN conversation_retention_days INTEGER NOT NULL DEFAULT 90;
ALTER TABLE tenants ADD COLUMN recording_retention_days INTEGER NOT NULL DEFAULT 30;

ALTER TABLE calls ADD COLUMN privacy_redacted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE calls ADD COLUMN recording_purged_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_calls_privacy_retention
    ON calls (tenant_id, ended_at, privacy_redacted_at);
CREATE INDEX idx_calls_recording_retention
    ON calls (tenant_id, ended_at, recording_purged_at);
