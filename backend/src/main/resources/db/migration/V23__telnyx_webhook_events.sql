CREATE TABLE telnyx_webhook_events (
    id UUID PRIMARY KEY,
    provider_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    call_control_id VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_telnyx_webhook_events_call
    ON telnyx_webhook_events(call_control_id, occurred_at);
