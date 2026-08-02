UPDATE agent_tools
SET tool_description = 'After explicit consent, send a booking confirmation SMS. Omit phone to use the verified number for a real call. Browser calls must collect a complete number; local numbers use the business country and foreign numbers need a country code.',
    parameters_schema = '{"type":"object","properties":{"phone":{"type":"string","format":"phone","description":"Destination phone number"},"message":{"type":"string","description":"SMS body"}},"required":["message"]}',
    action_effect = 'external_communication',
    confirmation_policy = 'explicit',
    is_active = CASE WHEN EXISTS (
        SELECT 1 FROM agent_integrations integration
        WHERE integration.agent_id = agent_tools.agent_id
          AND integration.provider = 'telnyx_sms'
          AND integration.enabled = TRUE
    ) THEN TRUE ELSE FALSE END
WHERE tool_name = 'send_confirmation_sms';

UPDATE agent_tools
SET tool_description = 'After explicit WhatsApp opt-in, send the configured approved template. Omit phone to use the verified number for a real call; otherwise collect a complete number.',
    parameters_schema = '{"type":"object","properties":{"phone":{"type":"string","format":"phone","description":"Recipient phone number"}},"required":[]}',
    action_effect = 'external_communication',
    confirmation_policy = 'explicit'
WHERE tool_name = 'send_whatsapp_message';
