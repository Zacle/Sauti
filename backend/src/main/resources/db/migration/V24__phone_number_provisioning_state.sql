ALTER TABLE agents ADD COLUMN phone_number_provider VARCHAR(40);
ALTER TABLE agents ADD COLUMN phone_number_status VARCHAR(40);
ALTER TABLE agents ADD COLUMN phone_number_order_id VARCHAR(120);
ALTER TABLE agents ADD COLUMN phone_number_assigned_at TIMESTAMP WITH TIME ZONE;

UPDATE agents
SET phone_number_provider = 'legacy',
    phone_number_status = 'active',
    phone_number_assigned_at = updated_at
WHERE twilio_phone_number IS NOT NULL;

CREATE INDEX idx_agents_phone_number_order
    ON agents(phone_number_provider, phone_number_order_id);
