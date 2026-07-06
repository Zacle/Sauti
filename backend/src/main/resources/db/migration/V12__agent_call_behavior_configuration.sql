ALTER TABLE agents ADD COLUMN barge_in_sensitivity DOUBLE PRECISION NOT NULL DEFAULT 0.70;
ALTER TABLE agents ADD COLUMN barge_in_grace_ms INT NOT NULL DEFAULT 300;
ALTER TABLE agents ADD COLUMN end_call_on_silence_seconds INT NOT NULL DEFAULT 600;
ALTER TABLE agents ADD COLUMN reminder_after_silence_seconds INT NOT NULL DEFAULT 10;
ALTER TABLE agents ADD COLUMN max_reminders INT NOT NULL DEFAULT 1;
ALTER TABLE agents ADD COLUMN detect_voicemail BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE agents ADD COLUMN handle_call_screening BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE agents ADD COLUMN dtmf_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agents ADD COLUMN stt_endpointing_ms INT NOT NULL DEFAULT 300;
ALTER TABLE agents ADD COLUMN stt_vocabulary_domain VARCHAR(40);
ALTER TABLE agents ADD COLUMN stt_boosted_keywords TEXT;
ALTER TABLE agents ADD COLUMN safety_guardrails TEXT;
ALTER TABLE agents ADD COLUMN post_call_extraction_fields TEXT;

ALTER TABLE calls ADD COLUMN call_summary TEXT;
ALTER TABLE calls ADD COLUMN call_successful BOOLEAN;
ALTER TABLE calls ADD COLUMN intent VARCHAR(120);
