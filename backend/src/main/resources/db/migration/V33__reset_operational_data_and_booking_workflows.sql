-- Intentional product reset requested before the template/workflow redesign.
-- Authentication identity is preserved: tenants, app_users, refresh_tokens,
-- auth_tokens, and Flyway history are not deleted.

ALTER TABLE agents ADD COLUMN IF NOT EXISTS booking_required_fields TEXT NOT NULL
    DEFAULT 'caller_name,caller_phone,service_type,appointment_at';
ALTER TABLE agents ADD COLUMN IF NOT EXISTS booking_notification_channels VARCHAR(255) NOT NULL
    DEFAULT 'dashboard,email';
ALTER TABLE agents ADD COLUMN IF NOT EXISTS booking_notification_recipient VARCHAR(320);

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS booking_reference VARCHAR(24);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS caller_email VARCHAR(320);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS captured_data TEXT NOT NULL DEFAULT '{}';
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS calendar_sync_status VARCHAR(30) NOT NULL DEFAULT 'pending';
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS calendar_sync_error VARCHAR(1000);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bookings_reference ON bookings(booking_reference);

UPDATE calls SET booking_id = NULL;
DELETE FROM integration_deliveries;
DELETE FROM post_call_jobs;
DELETE FROM mpesa_payment_requests;
DELETE FROM scheduled_calls;
DELETE FROM webhook_deliveries;
DELETE FROM call_turns;
DELETE FROM bookings;
DELETE FROM calls;
DELETE FROM knowledge_chunks;
DELETE FROM knowledge_documents;
DELETE FROM agent_integrations;
DELETE FROM agent_tools;
DELETE FROM agent_variables;
DELETE FROM calendar_credentials;
DELETE FROM integration_connections;
DELETE FROM agents;
DELETE FROM agent_templates;
DELETE FROM whatsapp_inbound_messages;
DELETE FROM telnyx_webhook_events;

UPDATE tenants
SET minutes_used_this_cycle = 0,
    webhook_url = NULL,
    webhook_secret = NULL;
