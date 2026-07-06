ALTER TABLE agents ADD COLUMN dtmf_termination_key VARCHAR(1) NOT NULL DEFAULT '#';
ALTER TABLE agents ADD COLUMN dtmf_input_timeout_seconds INT NOT NULL DEFAULT 5;
ALTER TABLE agents ADD COLUMN dtmf_max_digits INT NOT NULL DEFAULT 8;
ALTER TABLE agents ADD COLUMN dtmf_digit_mappings TEXT;
