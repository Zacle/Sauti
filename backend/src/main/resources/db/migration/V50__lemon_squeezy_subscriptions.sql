CREATE TABLE billing_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    provider_subscription_id VARCHAR(100) NOT NULL UNIQUE,
    provider_customer_id VARCHAR(100) NOT NULL,
    provider_order_id VARCHAR(100) NOT NULL,
    provider_product_id VARCHAR(100) NOT NULL,
    provider_variant_id VARCHAR(100) NOT NULL,
    plan VARCHAR(20) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL,
    provider_status VARCHAR(30) NOT NULL,
    test_mode BOOLEAN NOT NULL DEFAULT FALSE,
    renews_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    trial_ends_at TIMESTAMP WITH TIME ZONE,
    provider_updated_at TIMESTAMP WITH TIME ZONE,
    card_brand VARCHAR(30),
    card_last_four VARCHAR(4),
    update_payment_method_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE billing_provider_events (
    id UUID PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL UNIQUE,
    event_name VARCHAR(50) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_billing_provider_events_due
    ON billing_provider_events(status, next_attempt_at);
