ALTER TABLE communication_ledger_entries
    ADD COLUMN cost_basis VARCHAR(24) NOT NULL DEFAULT 'unpriced';

UPDATE communication_ledger_entries
SET cost_basis = CASE
    WHEN amount IS NULL THEN 'unpriced'
    WHEN category IN ('phone_number_purchase', 'phone_number_rental') THEN 'provider_quote'
    WHEN direction = 'credit' THEN 'credit'
    ELSE 'rate_card'
END;

ALTER TABLE communication_ledger_entries
    ADD CONSTRAINT chk_communication_ledger_cost_basis
    CHECK (cost_basis IN ('unpriced', 'rate_card', 'provider_quote', 'provider_confirmed', 'credit'));

CREATE TABLE provider_cost_reconciliation_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider VARCHAR(30) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    provider_resource_id VARCHAR(160) NOT NULL,
    local_reference VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    resource_occurred_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_provider_cost_reconciliation_resource
        UNIQUE (tenant_id, provider, resource_type, provider_resource_id),
    CONSTRAINT chk_provider_cost_reconciliation_status
        CHECK (status IN ('pending', 'retrying', 'reconciled', 'estimated', 'unavailable'))
);

CREATE INDEX idx_provider_cost_reconciliation_due
    ON provider_cost_reconciliation_jobs(status, next_attempt_at);
