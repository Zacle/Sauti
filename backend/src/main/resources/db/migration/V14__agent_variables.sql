CREATE TABLE agent_variables (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    var_key VARCHAR(100) NOT NULL,
    var_value TEXT NOT NULL DEFAULT '',
    display_label VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_agent_variables_agent_key UNIQUE (agent_id, var_key)
);

CREATE INDEX idx_agent_variables_agent ON agent_variables(agent_id);
