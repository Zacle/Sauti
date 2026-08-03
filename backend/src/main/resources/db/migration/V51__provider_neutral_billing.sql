ALTER TABLE billing_subscriptions
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'lemon_squeezy';

ALTER TABLE billing_provider_events
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'lemon_squeezy';

CREATE INDEX idx_billing_provider_events_provider_due
    ON billing_provider_events(provider, status, next_attempt_at);
