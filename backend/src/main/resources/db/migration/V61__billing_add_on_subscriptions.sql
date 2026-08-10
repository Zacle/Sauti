CREATE TABLE billing_add_on_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider VARCHAR(30) NOT NULL,
    provider_subscription_id VARCHAR(100) NOT NULL UNIQUE,
    provider_plan_id VARCHAR(100) NOT NULL,
    add_on VARCHAR(30) NOT NULL,
    provider_status VARCHAR(30) NOT NULL,
    test_mode BOOLEAN NOT NULL DEFAULT FALSE,
    renews_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    provider_updated_at TIMESTAMP WITH TIME ZONE,
    manage_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_billing_add_on_subscriptions_tenant
    ON billing_add_on_subscriptions(tenant_id);
