CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    business_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    plan VARCHAR(50) NOT NULL DEFAULT 'trial',
    plan_expires_at TIMESTAMP WITH TIME ZONE,
    monthly_minutes_limit INT NOT NULL DEFAULT 60,
    minutes_used_this_cycle INT NOT NULL DEFAULT 0,
    lemon_squeezy_customer_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'OWNER',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE agents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(100) NOT NULL,
    twilio_phone_number VARCHAR(20) UNIQUE,
    default_language VARCHAR(10) NOT NULL DEFAULT 'fr',
    supported_languages VARCHAR(255) NOT NULL DEFAULT 'fr',
    system_prompt TEXT NOT NULL,
    greeting_message TEXT NOT NULL,
    tts_voice_id VARCHAR(100),
    tts_provider VARCHAR(50) NOT NULL DEFAULT 'fake',
    llm_provider VARCHAR(50) NOT NULL DEFAULT 'fake',
    escalation_phrases TEXT,
    human_transfer_number VARCHAR(20),
    operating_hours TEXT,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE calls (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    twilio_call_sid VARCHAR(100) UNIQUE NOT NULL,
    caller_number VARCHAR(20),
    direction VARCHAR(10) NOT NULL,
    language_detected VARCHAR(10),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    duration_seconds INT,
    outcome VARCHAR(50),
    transcript TEXT,
    recording_url VARCHAR(500),
    sentiment VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE call_turns (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES calls(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    turn_index INT NOT NULL,
    caller_transcript TEXT NOT NULL,
    agent_response TEXT NOT NULL,
    language VARCHAR(10) NOT NULL,
    stt_latency_ms INT NOT NULL DEFAULT 0,
    llm_latency_ms INT NOT NULL DEFAULT 0,
    tts_latency_ms INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_agents_tenant ON agents(tenant_id);
CREATE INDEX idx_calls_tenant_started ON calls(tenant_id, started_at DESC);
CREATE INDEX idx_call_turns_call ON call_turns(call_id, turn_index);
