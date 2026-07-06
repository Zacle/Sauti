CREATE TABLE integration_connections (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider VARCHAR(50) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'connected',
    encrypted_credentials TEXT,
    configuration TEXT NOT NULL DEFAULT '{}',
    external_account_id VARCHAR(255),
    last_tested_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE agent_integrations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    provider VARCHAR(50) NOT NULL,
    connection_id UUID REFERENCES integration_connections(id) ON DELETE SET NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    configuration TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_agent_integration_provider UNIQUE (agent_id, provider)
);

CREATE TABLE post_call_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    call_id UUID NOT NULL REFERENCES calls(id),
    status VARCHAR(30) NOT NULL DEFAULT 'pending_analysis',
    test BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_post_call_job_call UNIQUE (call_id)
);

CREATE TABLE integration_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    job_id UUID NOT NULL REFERENCES post_call_jobs(id) ON DELETE CASCADE,
    call_id UUID NOT NULL REFERENCES calls(id),
    agent_integration_id UUID NOT NULL REFERENCES agent_integrations(id),
    provider VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    response_code INT,
    last_error TEXT,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_integration_connections_tenant ON integration_connections(tenant_id, provider);
CREATE INDEX idx_agent_integrations_agent ON agent_integrations(tenant_id, agent_id);
CREATE INDEX idx_post_call_jobs_pending ON post_call_jobs(status, next_attempt_at);
CREATE INDEX idx_integration_deliveries_pending ON integration_deliveries(status, next_attempt_at);
CREATE INDEX idx_integration_deliveries_tenant ON integration_deliveries(tenant_id, created_at);
