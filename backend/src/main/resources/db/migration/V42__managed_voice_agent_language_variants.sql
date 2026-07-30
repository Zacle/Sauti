ALTER TABLE managed_voice_agent_bindings
    ADD COLUMN language VARCHAR(16);

UPDATE managed_voice_agent_bindings
SET language = (
    SELECT agents.default_language
    FROM agents
    WHERE agents.id = managed_voice_agent_bindings.agent_id
);

ALTER TABLE managed_voice_agent_bindings
    ALTER COLUMN language SET NOT NULL;

ALTER TABLE managed_voice_agent_bindings
    DROP CONSTRAINT uq_managed_voice_agent_provider;

ALTER TABLE managed_voice_agent_bindings
    ADD CONSTRAINT uq_managed_voice_agent_provider
        UNIQUE (agent_id, provider, language);

