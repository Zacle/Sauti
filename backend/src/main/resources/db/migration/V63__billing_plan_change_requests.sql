CREATE TABLE billing_plan_change_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    provider_subscription_id VARCHAR(100) NOT NULL,
    current_plan VARCHAR(20) NOT NULL,
    target_plan VARCHAR(20) NOT NULL,
    target_interval VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_billing_plan_change_requests_status
    ON billing_plan_change_requests (status, updated_at DESC);
