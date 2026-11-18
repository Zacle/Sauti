-- Phase 3: activity log, workspace API keys and platform administrators with two-factor sign-in.

-- One row per security-relevant change. tenant_id is set for workspace activity; user_id is set for
-- account activity (sign-ins, password changes). No foreign keys, so history survives deletions.
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    user_id UUID,
    actor_user_id UUID,
    actor_api_key_id UUID,
    actor_label VARCHAR(255) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),
    target_id VARCHAR(64),
    summary VARCHAR(500) NOT NULL,
    ip_hash VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_events_tenant_time ON audit_events(tenant_id, created_at);
CREATE INDEX idx_audit_events_user_time ON audit_events(user_id, created_at);

-- Machine credentials for one workspace. Only a SHA-256 hash of the key is stored; the prefix lets
-- people recognise a key without revealing it.
CREATE TABLE workspace_api_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    access VARCHAR(20) NOT NULL,
    created_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_workspace_api_keys_hash UNIQUE (key_hash),
    CONSTRAINT fk_workspace_api_keys_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE INDEX idx_workspace_api_keys_tenant ON workspace_api_keys(tenant_id, created_at);

-- Platform administrators are a separate identity: a row here plus a verified authenticator code
-- on every admin sign-in. The configured email allowlist only bootstraps the first rows.
CREATE TABLE platform_admins (
    user_id UUID PRIMARY KEY,
    totp_secret_encrypted VARCHAR(512),
    totp_confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_admins_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

-- Sessions started after a verified authenticator code carry platform-admin rights; others never do.
ALTER TABLE refresh_tokens ADD COLUMN elevated BOOLEAN NOT NULL DEFAULT FALSE;

-- Accounts can be deleted; the row is kept, anonymised, so references in history stay valid.
ALTER TABLE app_users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
