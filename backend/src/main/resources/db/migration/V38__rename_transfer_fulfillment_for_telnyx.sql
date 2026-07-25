UPDATE agent_tools
SET fulfillment_type = 'call_transfer'
WHERE fulfillment_type = 'twilio_transfer';
