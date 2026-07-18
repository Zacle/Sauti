CREATE TABLE workspace_notifications (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type VARCHAR(60) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    href VARCHAR(300) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID,
    payload TEXT NOT NULL DEFAULT '{}',
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_workspace_notifications_tenant_created
    ON workspace_notifications(tenant_id, created_at DESC);
CREATE INDEX idx_workspace_notifications_tenant_unread
    ON workspace_notifications(tenant_id, read_at);
