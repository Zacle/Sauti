UPDATE billing_provider_events
SET status = 'retrying',
    attempts = 0,
    next_attempt_at = CURRENT_TIMESTAMP,
    last_error = NULL
WHERE provider = 'whop'
  AND status = 'failed'
  AND event_name LIKE 'membership.%'
  AND last_error = 'Workspace already has a different subscription';
