CREATE TABLE pilot_invitations (
    id UUID PRIMARY KEY,
    demo_request_id UUID NOT NULL UNIQUE REFERENCES demo_requests(id),
    business_name VARCHAR(120) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_pilot_invitations_email ON pilot_invitations(email);
CREATE INDEX idx_pilot_invitations_expires_at ON pilot_invitations(expires_at);
