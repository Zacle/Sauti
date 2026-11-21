-- FR4: every tool that changes something needs the caller's confirmation, and every such action taken
-- during a call is recorded.

-- Bring stored policies up to the safety floor that the runtime now enforces anyway.
UPDATE agent_tools SET action_effect = 'external_communication', confirmation_policy = 'explicit'
WHERE fulfillment_type = 'sauti_sms' AND action_effect = 'read_only';

UPDATE agent_tools SET action_effect = 'transfer', confirmation_policy = 'explicit'
WHERE fulfillment_type = 'call_transfer' AND action_effect <> 'transfer';

UPDATE agent_tools SET confirmation_policy = 'explicit'
WHERE action_effect <> 'read_only' AND confirmation_policy = 'none';

UPDATE agent_tools SET confirmation_policy = 'explicit'
WHERE confirmation_policy = 'verified_review' AND fulfillment_type <> 'sauti_calendar';

-- One row per attempt to run a side-effecting tool during a call: awaiting the caller's confirmation,
-- started, performed, not performed or failed. It also stops a provider's repeated delivery of the
-- same tool call from running the action twice, across restarts. Only a short summary is kept, never
-- the tool's arguments or result, so it holds no phone numbers.
CREATE TABLE call_tool_actions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    call_id UUID NOT NULL REFERENCES calls(id) ON DELETE CASCADE,
    invocation_id VARCHAR(255) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    effect VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_call_tool_actions_invocation UNIQUE (call_id, invocation_id)
);

CREATE INDEX idx_call_tool_actions_call ON call_tool_actions(call_id, created_at);
