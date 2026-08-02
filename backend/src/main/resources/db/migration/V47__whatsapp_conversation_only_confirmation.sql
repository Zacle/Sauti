UPDATE agent_tools
SET tool_description = 'After explicit confirmation, send the configured approved template only to the customer in the current WhatsApp conversation.',
    parameters_schema = '{"type":"object","properties":{},"required":[]}',
    action_effect = 'external_communication',
    confirmation_policy = 'explicit'
WHERE tool_name = 'send_whatsapp_message';
