-- FR2 phase 3: edits can wait in a draft without touching live calls; every publish is kept as a
-- numbered version that can be restored. Snapshots are JSON so old versions survive schema changes.

-- At most one unpublished draft per agent: the whole setup request, saved as the owner left it.
CREATE TABLE agent_drafts (
    agent_id UUID PRIMARY KEY REFERENCES agents(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    setup_json TEXT NOT NULL,
    saved_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- What was live after each publish: the agent settings plus every business detail and its value.
CREATE TABLE agent_versions (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    version INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    note VARCHAR(200),
    published_by VARCHAR(255),
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_agent_versions_agent_version UNIQUE (agent_id, version)
);

CREATE INDEX idx_agent_versions_agent ON agent_versions(agent_id, version DESC);
