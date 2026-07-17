-- Move agents that still use the original default silence settings to the
-- conversational 30-second reminder / 30-second final-grace policy. Explicitly
-- customized configurations are preserved.
UPDATE agents
SET reminder_after_silence_seconds = 30,
    end_call_on_silence_seconds = 60
WHERE reminder_after_silence_seconds = 10
  AND end_call_on_silence_seconds = 600
  AND max_reminders = 1;
