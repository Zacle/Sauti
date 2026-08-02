CREATE TABLE billing_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    status VARCHAR(20) NOT NULL DEFAULT 'preview',
    enforcement_mode VARCHAR(12) NOT NULL DEFAULT 'observe',
    billing_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    monthly_spending_limit DECIMAL(19,4),
    low_balance_threshold DECIMAL(19,4) NOT NULL DEFAULT 10,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE communication_ledger_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    billing_account_id UUID NOT NULL REFERENCES billing_accounts(id),
    direction VARCHAR(10) NOT NULL,
    category VARCHAR(40) NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    amount DECIMAL(19,4),
    currency VARCHAR(3),
    idempotency_key VARCHAR(160) NOT NULL,
    external_reference VARCHAR(160),
    description VARCHAR(500) NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_communication_ledger_tenant_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_communication_ledger_direction CHECK (direction IN ('credit', 'debit')),
    CONSTRAINT chk_communication_ledger_quantity CHECK (quantity > 0),
    CONSTRAINT chk_communication_ledger_amount CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT chk_communication_ledger_currency CHECK (
        (amount IS NULL AND currency IS NULL) OR (amount IS NOT NULL AND currency IS NOT NULL)
    )
);

CREATE INDEX idx_communication_ledger_tenant_created
    ON communication_ledger_entries(tenant_id, created_at DESC);
