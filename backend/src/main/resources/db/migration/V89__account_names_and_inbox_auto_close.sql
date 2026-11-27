-- FR7 phase 3: a name for each person, shown on their WhatsApp replies, and chats that close on their own.

-- What colleagues see on this person's replies. Empty until they set it; their email is shown meanwhile.
ALTER TABLE app_users ADD COLUMN display_name VARCHAR(80);

-- Why a conversation was closed: 'team' (someone closed it) or 'inactive' (7 days without a message).
ALTER TABLE whatsapp_conversations ADD COLUMN closed_reason VARCHAR(20);
UPDATE whatsapp_conversations SET closed_reason = 'team' WHERE status = 'closed';

-- The hourly sweep looks for open conversations whose last message is older than the cutoff.
CREATE INDEX idx_whatsapp_conversations_status_last ON whatsapp_conversations(status, last_message_at);
