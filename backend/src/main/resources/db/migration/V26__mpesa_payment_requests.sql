CREATE TABLE mpesa_payment_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    call_id UUID NOT NULL REFERENCES calls(id),
    connection_id UUID NOT NULL REFERENCES integration_connections(id),
    recipient_phone VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    account_reference VARCHAR(50) NOT NULL,
    description VARCHAR(100) NOT NULL,
    merchant_request_id VARCHAR(100),
    checkout_request_id VARCHAR(100) UNIQUE,
    status VARCHAR(30) NOT NULL,
    result_code INT,
    result_description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_mpesa_requests_tenant ON mpesa_payment_requests(tenant_id, created_at);
