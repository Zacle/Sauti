CREATE TABLE managed_voice_agent_bindings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    external_agent_id VARCHAR(255) NOT NULL,
    external_version_id VARCHAR(255),
    blueprint_hash VARCHAR(64) NOT NULL,
    external_resources_json TEXT NOT NULL DEFAULT '{}',
    last_synced_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_managed_voice_agent_provider UNIQUE (agent_id, provider)
);

CREATE INDEX idx_managed_voice_bindings_tenant
    ON managed_voice_agent_bindings(tenant_id, provider);
