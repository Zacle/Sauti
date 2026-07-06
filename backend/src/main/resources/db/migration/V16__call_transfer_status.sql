ALTER TABLE calls ADD COLUMN transfer_status VARCHAR(30);
ALTER TABLE calls ADD COLUMN transfer_target_number VARCHAR(40);
ALTER TABLE calls ADD COLUMN transfer_child_call_sid VARCHAR(100);
ALTER TABLE calls ADD COLUMN transfer_failure_reason VARCHAR(500);
ALTER TABLE calls ADD COLUMN transfer_requested_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE calls ADD COLUMN transfer_completed_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_calls_transfer_status ON calls(transfer_status);

UPDATE agent_tools
SET fulfillment_type = 'twilio_transfer'
WHERE tool_name = 'transfer_to_human';
