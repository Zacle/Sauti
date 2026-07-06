ALTER TABLE agents ADD COLUMN knowledge_base TEXT;

CREATE TABLE calendar_credentials (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider VARCHAR(32) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    token_expiry TIMESTAMP WITH TIME ZONE,
    external_id TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE agent_tools (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    tool_name VARCHAR(64) NOT NULL,
    tool_description TEXT NOT NULL,
    parameters_schema TEXT NOT NULL,
    fulfillment_type VARCHAR(32) NOT NULL,
    webhook_url TEXT,
    webhook_method VARCHAR(8) DEFAULT 'POST',
    auth_type VARCHAR(16) DEFAULT 'none',
    auth_credential TEXT,
    auth_header_name VARCHAR(64),
    calendar_type VARCHAR(32),
    calendar_credential_id UUID REFERENCES calendar_credentials(id),
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_agent_tool_name UNIQUE (agent_id, tool_name)
);

CREATE INDEX idx_agent_tools_agent_active ON agent_tools(agent_id, is_active, display_order);
