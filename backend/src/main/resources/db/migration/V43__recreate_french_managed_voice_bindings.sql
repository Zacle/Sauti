-- French was the original default for existing agents when language-specific
-- bindings were introduced. Those migrated rows retain the legacy Telnyx
-- assistant identity, while newly created language variants use clean
-- assistants. Managed bindings are rebuildable provider caches; removing only
-- the affected French Telnyx rows lets startup reconciliation recreate them.
DELETE FROM managed_voice_agent_bindings
WHERE provider = 'telnyx'
  AND language = 'fr';
