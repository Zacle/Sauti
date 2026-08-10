CREATE TABLE billing_payment_notifications (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider VARCHAR(30) NOT NULL,
    provider_payment_id VARCHAR(100) NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    business_name VARCHAR(200) NOT NULL,
    purchase_description VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    card_last4 VARCHAR(4),
    test_mode BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_billing_payment_notification_provider_payment
        UNIQUE (provider, provider_payment_id)
);

CREATE INDEX idx_billing_payment_notifications_due
    ON billing_payment_notifications(status, next_attempt_at);
