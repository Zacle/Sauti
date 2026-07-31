UPDATE bookings
SET calendar_sync_status = 'pending',
    calendar_sync_error = NULL,
    calendar_sync_attempts = 0,
    calendar_sync_next_attempt_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE calendar_sync_status = 'pending_owner_action'
  AND external_event_id IS NULL
  AND status = 'confirmed'
  AND agent_id IN (
      SELECT id
      FROM agents
      WHERE LOWER(COALESCE(calendar_provider, '')) = 'google calendar'
  );
