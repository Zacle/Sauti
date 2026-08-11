CREATE TABLE billing_provider_evidence (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL UNIQUE REFERENCES billing_provider_events(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider VARCHAR(30) NOT NULL,
    record_type VARCHAR(30) NOT NULL,
    event_name VARCHAR(50) NOT NULL,
    provider_resource_id VARCHAR(100) NOT NULL,
    provider_payment_id VARCHAR(100),
    provider_membership_id VARCHAR(100),
    provider_plan_id VARCHAR(100),
    normalized_status VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 4),
    currency VARCHAR(3),
    test_mode BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_billing_provider_evidence_tenant_occurred
    ON billing_provider_evidence(tenant_id, occurred_at DESC);
CREATE INDEX idx_billing_provider_evidence_payment
    ON billing_provider_evidence(provider, provider_payment_id);

-- Re-run already verified events through the idempotent normalizer so sandbox
-- acceptance completed before this migration is retained as normalized proof.
UPDATE billing_provider_events
SET status = 'pending', next_attempt_at = CURRENT_TIMESTAMP, last_error = NULL
WHERE provider = 'whop'
  AND status = 'deferred'
  AND (event_name LIKE 'payment.%'
    OR event_name LIKE 'refund.%'
    OR event_name LIKE 'dispute.%');
