CREATE TABLE pilot_readiness_reviews (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id),
    support_contact_name VARCHAR(160),
    support_contact_email VARCHAR(254),
    support_contact_phone VARCHAR(40),
    launch_notes VARCHAR(1000),
    launch_approved BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(254),
    approved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

