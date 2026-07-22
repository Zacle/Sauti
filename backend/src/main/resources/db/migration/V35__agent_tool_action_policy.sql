ALTER TABLE agent_tools ADD COLUMN action_effect VARCHAR(32) NOT NULL DEFAULT 'read_only';
ALTER TABLE agent_tools ADD COLUMN confirmation_policy VARCHAR(32) NOT NULL DEFAULT 'none';

UPDATE agent_tools SET action_effect = 'data_write', confirmation_policy = 'verified_review'
WHERE tool_name = 'book_slot';

UPDATE agent_tools SET action_effect = 'data_write', confirmation_policy = 'explicit'
WHERE tool_name IN ('reschedule_booking', 'cancel_booking', 'update_google_sheet_row');

UPDATE agent_tools SET action_effect = 'external_communication', confirmation_policy = 'none'
WHERE tool_name = 'send_confirmation_sms';

UPDATE agent_tools SET action_effect = 'external_communication', confirmation_policy = 'explicit'
WHERE tool_name = 'send_whatsapp_message';

UPDATE agent_tools SET action_effect = 'financial', confirmation_policy = 'explicit'
WHERE tool_name = 'request_mpesa_payment';

UPDATE agent_tools SET action_effect = 'transfer', confirmation_policy = 'explicit'
WHERE tool_name = 'transfer_to_human';

UPDATE agent_tools SET action_effect = 'terminal', confirmation_policy = 'explicit'
WHERE tool_name = 'end_call';

UPDATE agent_tools SET action_effect = 'data_write', confirmation_policy = 'explicit'
WHERE tool_name = 'call_custom_webhook';

UPDATE agent_tools SET action_effect = 'read_only', confirmation_policy = 'none'
WHERE tool_name IN ('get_business_hours', 'check_availability', 'lookup_google_sheet_row', 'check_mpesa_payment');

UPDATE agent_tools SET action_effect = 'read_only', confirmation_policy = 'none'
WHERE fulfillment_type = 'webhook' AND UPPER(COALESCE(webhook_method, 'POST')) = 'GET';

UPDATE agent_tools SET action_effect = 'data_write', confirmation_policy = 'explicit'
WHERE fulfillment_type = 'webhook' AND UPPER(COALESCE(webhook_method, 'POST')) <> 'GET';
