ALTER TABLE agents ADD COLUMN IF NOT EXISTS booking_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agents ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    call_id UUID REFERENCES calls(id),
    caller_name VARCHAR(255) NOT NULL,
    caller_phone VARCHAR(20) NOT NULL,
    service_type VARCHAR(255) NOT NULL,
    booked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    appointment_at TIMESTAMP WITH TIME ZONE NOT NULL,
    external_event_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'confirmed',
    confirmation_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE calls ADD COLUMN IF NOT EXISTS booking_id UUID REFERENCES bookings(id);
ALTER TABLE calls ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(255);

CREATE INDEX idx_bookings_tenant_appointment ON bookings(tenant_id, appointment_at DESC);
CREATE INDEX idx_bookings_agent ON bookings(agent_id);
