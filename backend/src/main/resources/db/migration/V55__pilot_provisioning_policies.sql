CREATE TABLE pilot_provisioning_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    monthly_budget NUMERIC(19,4) NOT NULL DEFAULT 0,
    phone_numbers_approved BOOLEAN NOT NULL DEFAULT FALSE,
    live_calling_approved BOOLEAN NOT NULL DEFAULT FALSE,
    sms_approved BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(254),
    approved_at TIMESTAMP WITH TIME ZONE,
    notes VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_pilot_provisioning_status ON pilot_provisioning_policies(status, updated_at);
