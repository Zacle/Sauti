CREATE TABLE whatsapp_conversations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    phone_number_id VARCHAR(100) NOT NULL,
    customer_number VARCHAR(50) NOT NULL,
    customer_name VARCHAR(255),
    mode VARCHAR(20) NOT NULL DEFAULT 'ai',
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    unread_count INT NOT NULL DEFAULT 0,
    last_message_preview VARCHAR(500),
    last_message_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_whatsapp_conversation_customer UNIQUE (agent_id, customer_number)
);

CREATE INDEX idx_whatsapp_conversations_tenant_activity
    ON whatsapp_conversations(tenant_id, last_message_at DESC);

CREATE TABLE whatsapp_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES whatsapp_conversations(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    provider_message_id VARCHAR(255),
    direction VARCHAR(20) NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    body TEXT,
    media_id VARCHAR(255),
    media_mime_type VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_whatsapp_message_provider UNIQUE (provider_message_id)
);

CREATE INDEX idx_whatsapp_messages_conversation_time
    ON whatsapp_messages(conversation_id, created_at);
