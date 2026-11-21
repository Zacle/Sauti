-- FR2 phase 3: business facts entered once per workspace and shared by its agents. An agent's own
-- variable value still wins; the profile fills any detail the agent leaves empty.
CREATE TABLE business_profiles (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    display_name VARCHAR(160),
    description VARCHAR(500),
    address VARCHAR(500),
    phone VARCHAR(40),
    email VARCHAR(254),
    website VARCHAR(300),
    cancellation_policy VARCHAR(2000),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Bookable services with a typed price and duration, in the owner's order.
CREATE TABLE business_profile_services (
    tenant_id UUID NOT NULL REFERENCES business_profiles(tenant_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    price_amount NUMERIC(12, 2),
    currency VARCHAR(3),
    duration_minutes INTEGER,
    description VARCHAR(300),
    PRIMARY KEY (tenant_id, position)
);

CREATE TABLE business_profile_faqs (
    tenant_id UUID NOT NULL REFERENCES business_profiles(tenant_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    question VARCHAR(300) NOT NULL,
    answer VARCHAR(1000) NOT NULL,
    PRIMARY KEY (tenant_id, position)
);
