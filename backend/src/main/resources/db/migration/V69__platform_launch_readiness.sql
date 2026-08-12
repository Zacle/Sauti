CREATE TABLE platform_launch_readiness (
    id VARCHAR(40) PRIMARY KEY,
    security_review_completed BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_legal_review_completed BOOLEAN NOT NULL DEFAULT FALSE,
    google_verification_completed BOOLEAN NOT NULL DEFAULT FALSE,
    live_acceptance_completed BOOLEAN NOT NULL DEFAULT FALSE,
    general_availability_approved BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(2000),
    reviewed_by VARCHAR(320),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
