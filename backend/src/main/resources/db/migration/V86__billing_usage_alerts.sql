-- One row per workspace, kind (minutes or messages), cycle, allowance and threshold (80% or 100%), so
-- each alert is sent once. A grant of extra minutes or messages changes the allowance, so the new
-- allowance can alert again.
CREATE TABLE billing_usage_alerts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    kind VARCHAR(20) NOT NULL,
    cycle_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cycle_ends_at TIMESTAMP WITH TIME ZONE,
    included_amount INTEGER NOT NULL,
    threshold_percent INTEGER NOT NULL,
    used_amount INTEGER NOT NULL,
    trial BOOLEAN NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    business_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_billing_usage_alert
        UNIQUE (tenant_id, kind, cycle_starts_at, included_amount, threshold_percent)
);

CREATE INDEX idx_billing_usage_alerts_due ON billing_usage_alerts(status, next_attempt_at);
