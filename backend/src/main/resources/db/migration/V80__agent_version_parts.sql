-- Versions now also record direct edits (a single business detail, tools, documents, integrations,
-- timezone), not only publishes, and keep the parts owned by other modules beside the settings.

-- publish: saved through setup or a restore; edit: recorded automatically after a direct edit.
ALTER TABLE agent_versions ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'publish';

-- JSON object keyed by part ("tools", "integrations", "documents"). Null for versions saved before.
ALTER TABLE agent_versions ADD COLUMN parts_json TEXT;
