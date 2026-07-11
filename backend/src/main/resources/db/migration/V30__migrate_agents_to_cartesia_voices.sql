UPDATE agents target
SET tts_voice_id = (
    SELECT MIN(source.tts_voice_id)
    FROM agents source
    WHERE source.tts_voice_id LIKE 'cartesia:%'
      AND source.tenant_id = target.tenant_id
)
WHERE (tts_voice_id IS NULL OR tts_voice_id NOT LIKE 'cartesia:%')
  AND EXISTS (
    SELECT 1
    FROM agents source
    WHERE source.tts_voice_id LIKE 'cartesia:%'
      AND source.tenant_id = target.tenant_id
  );
