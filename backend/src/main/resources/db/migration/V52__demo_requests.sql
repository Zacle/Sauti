CREATE TABLE demo_requests (
    id UUID PRIMARY KEY,
    business_name VARCHAR(120) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    phone VARCHAR(40),
    industry VARCHAR(80) NOT NULL,
    monthly_call_volume VARCHAR(40) NOT NULL,
    channels VARCHAR(200) NOT NULL,
    primary_use_case VARCHAR(500) NOT NULL,
    notes VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'new',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_demo_requests_email_created
    ON demo_requests(email, created_at);

CREATE INDEX idx_demo_requests_status_created
    ON demo_requests(status, created_at);
