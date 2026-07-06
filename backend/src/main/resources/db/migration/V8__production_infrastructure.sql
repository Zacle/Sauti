ALTER TABLE tenants ADD COLUMN webhook_url VARCHAR(1000);
ALTER TABLE tenants ADD COLUMN webhook_secret VARCHAR(255);

ALTER TABLE agents ADD COLUMN twilio_phone_number_sid VARCHAR(100);
ALTER TABLE calls ADD COLUMN recording_sid VARCHAR(100);

CREATE TABLE scheduled_calls (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID REFERENCES agents(id),
    booking_id UUID REFERENCES bookings(id),
    call_type VARCHAR(50) NOT NULL,
    target_phone VARCHAR(30) NOT NULL,
    scheduled_for TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    twilio_call_sid VARCHAR(100),
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_scheduled_calls_due ON scheduled_calls(status, scheduled_for);
CREATE INDEX idx_scheduled_calls_tenant ON scheduled_calls(tenant_id, scheduled_for DESC);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    event_type VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    endpoint_url VARCHAR(1000) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_status_code INT,
    success BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries(success, next_attempt_at);
CREATE INDEX idx_webhook_deliveries_tenant ON webhook_deliveries(tenant_id, created_at DESC);
