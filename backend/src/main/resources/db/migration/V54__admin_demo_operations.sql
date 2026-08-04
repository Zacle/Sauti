ALTER TABLE demo_requests ADD COLUMN assigned_to VARCHAR(254);
ALTER TABLE demo_requests ADD COLUMN internal_notes VARCHAR(4000);
ALTER TABLE demo_requests ADD COLUMN rejected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE demo_requests ADD COLUMN rejected_reason VARCHAR(1000);

ALTER TABLE pilot_invitations ADD COLUMN revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pilot_invitations ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'pending';
ALTER TABLE pilot_invitations ADD COLUMN delivery_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pilot_invitations ADD COLUMN last_delivery_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pilot_invitations ADD COLUMN sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pilot_invitations ADD COLUMN last_delivery_error VARCHAR(200);

CREATE TABLE platform_admin_audit_events (
    id UUID PRIMARY KEY,
    actor_email VARCHAR(254) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_platform_admin_audit_created ON platform_admin_audit_events(created_at);
CREATE INDEX idx_platform_admin_audit_resource ON platform_admin_audit_events(resource_type, resource_id);
