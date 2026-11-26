-- FR7 phase 2: who on the team sent each reply, template sends, and an inbox that pages and polls for changes.

-- The team member who sent a reply. The email is kept as written so the label survives the person leaving.
ALTER TABLE whatsapp_messages ADD COLUMN sent_by_user_id UUID;
ALTER TABLE whatsapp_messages ADD COLUMN sent_by VARCHAR(320);
-- The approved template a staff message was sent from, when it was one.
ALTER TABLE whatsapp_messages ADD COLUMN template_name VARCHAR(512);
CREATE INDEX idx_whatsapp_messages_conversation_updated ON whatsapp_messages(conversation_id, updated_at);

-- The inbox lists a workspace's open or closed conversations newest first, and polls for what changed.
CREATE INDEX idx_whatsapp_conversations_tenant_status_last
    ON whatsapp_conversations(tenant_id, status, last_message_at);
CREATE INDEX idx_whatsapp_conversations_tenant_updated ON whatsapp_conversations(tenant_id, updated_at);
-- Paging is ordered by the last message; every conversation gets one.
UPDATE whatsapp_conversations SET last_message_at = created_at WHERE last_message_at IS NULL;
