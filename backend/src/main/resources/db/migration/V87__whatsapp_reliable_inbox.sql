-- FR7 phase 1: no customer message is lost, staff can see what needs them, quiet conversations end.

-- What arrived, kept with the claim so a message lost to a restart or a failure can be processed again.
ALTER TABLE whatsapp_inbound_messages ADD COLUMN customer_name VARCHAR(255);
ALTER TABLE whatsapp_inbound_messages ADD COLUMN body TEXT;
ALTER TABLE whatsapp_inbound_messages ADD COLUMN media_id VARCHAR(255);
ALTER TABLE whatsapp_inbound_messages ADD COLUMN media_mime_type VARCHAR(120);
ALTER TABLE whatsapp_inbound_messages ADD COLUMN attempts INT NOT NULL DEFAULT 0;
-- Messages from before this change carry no content to replay, so they are never retried.
UPDATE whatsapp_inbound_messages SET attempts = 3;
CREATE INDEX idx_whatsapp_inbound_messages_retry ON whatsapp_inbound_messages(status, updated_at);

-- Who wrote each message: customer, ai or staff. Older outbound messages stay unknown.
ALTER TABLE whatsapp_messages ADD COLUMN author VARCHAR(20);
UPDATE whatsapp_messages SET author = 'customer' WHERE direction = 'inbound';

-- The customer's last message opens WhatsApp's 24-hour reply window and keeps the conversation going.
ALTER TABLE whatsapp_conversations ADD COLUMN last_customer_message_at TIMESTAMP WITH TIME ZONE;
UPDATE whatsapp_conversations SET last_customer_message_at = (
    SELECT MAX(m.created_at) FROM whatsapp_messages m
    WHERE m.conversation_id = whatsapp_conversations.id AND m.direction = 'inbound'
);

-- Set when the last customer message is waiting on the team: the agent could not reply, replies are
-- paused, or a person has taken over.
ALTER TABLE whatsapp_conversations ADD COLUMN needs_attention BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE whatsapp_conversations ADD COLUMN attention_reason VARCHAR(40);

-- Whether the conversation's call record is still open; it is closed 24 hours after the last customer
-- message so its summary and follow-ups run.
ALTER TABLE whatsapp_conversations ADD COLUMN session_open BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE whatsapp_conversations SET session_open = TRUE;
CREATE INDEX idx_whatsapp_conversations_open_session
    ON whatsapp_conversations(session_open, last_customer_message_at);
