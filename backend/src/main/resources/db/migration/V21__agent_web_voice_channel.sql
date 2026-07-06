ALTER TABLE agents ADD COLUMN web_voice_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agents ADD COLUMN web_voice_public_id VARCHAR(36);
UPDATE agents SET web_voice_public_id = CAST(id AS VARCHAR(36)) WHERE web_voice_public_id IS NULL;
ALTER TABLE agents ALTER COLUMN web_voice_public_id SET NOT NULL;
CREATE UNIQUE INDEX idx_agents_web_voice_public_id ON agents(web_voice_public_id);
ALTER TABLE agents ADD COLUMN web_voice_allowed_origins TEXT;
ALTER TABLE agents ADD COLUMN web_voice_require_consent BOOLEAN NOT NULL DEFAULT TRUE;
