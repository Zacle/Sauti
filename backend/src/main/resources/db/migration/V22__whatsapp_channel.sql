ALTER TABLE agents ADD COLUMN whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agents ADD COLUMN whatsapp_phone_number_id VARCHAR(100);

CREATE UNIQUE INDEX idx_agents_whatsapp_phone_number_id
    ON agents(whatsapp_phone_number_id);

CREATE TABLE whatsapp_inbound_messages (
    id UUID PRIMARY KEY,
    provider_message_id VARCHAR(255) NOT NULL UNIQUE,
    phone_number_id VARCHAR(100) NOT NULL,
    customer_number VARCHAR(50) NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_whatsapp_inbound_messages_channel
    ON whatsapp_inbound_messages(phone_number_id, customer_number, created_at DESC);
