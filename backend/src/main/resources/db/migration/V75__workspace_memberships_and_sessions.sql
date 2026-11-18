-- Phase 1 of multi-workspace identity (expand step).
-- People can now belong to several workspaces with a role in each one.
-- Legacy columns (app_users.tenant_id, app_users.role, tenants.email) stay in place but are no
-- longer written; a later contract migration drops them once this release is stable.

CREATE TABLE workspace_memberships (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_workspace_memberships PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT fk_workspace_memberships_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_memberships_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_workspace_memberships_user ON workspace_memberships(user_id);

-- Every existing person becomes the owner of the workspace they belong to today.
INSERT INTO workspace_memberships (tenant_id, user_id, role, created_at, updated_at)
SELECT tenant_id, id, 'OWNER', created_at, CURRENT_TIMESTAMP
FROM app_users
WHERE tenant_id IS NOT NULL;

CREATE TABLE workspace_invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    invited_by_user_id UUID,
    accepted_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_workspace_invitations_token UNIQUE (token_hash),
    CONSTRAINT fk_workspace_invitations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_invitations_inviter FOREIGN KEY (invited_by_user_id) REFERENCES app_users(id) ON DELETE SET NULL,
    CONSTRAINT fk_workspace_invitations_acceptor FOREIGN KEY (accepted_by_user_id) REFERENCES app_users(id) ON DELETE SET NULL
);

CREATE INDEX idx_workspace_invitations_tenant ON workspace_invitations(tenant_id, created_at);
CREATE INDEX idx_workspace_invitations_email ON workspace_invitations(email);

-- External sign-in identities are linked by the provider's stable subject, never by email.
CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_user_identities_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_user_identities_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_identities_user ON user_identities(user_id);

ALTER TABLE app_users ADD COLUMN last_workspace_id UUID;
ALTER TABLE app_users
    ADD CONSTRAINT fk_app_users_last_workspace FOREIGN KEY (last_workspace_id) REFERENCES tenants(id) ON DELETE SET NULL;
UPDATE app_users SET last_workspace_id = tenant_id;
ALTER TABLE app_users ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE app_users ALTER COLUMN role DROP NOT NULL;
-- Accounts created through Google have no password.
ALTER TABLE app_users ALTER COLUMN password_hash DROP NOT NULL;

-- One person may own several workspaces, so the workspace contact email is no longer unique.
ALTER TABLE tenants ADD COLUMN contact_email VARCHAR(255);
UPDATE tenants SET contact_email = email;
ALTER TABLE tenants ALTER COLUMN contact_email SET NOT NULL;
ALTER TABLE tenants ALTER COLUMN email DROP NOT NULL;

-- Refresh tokens are grouped into session families so reuse of a rotated token ends the session.
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = id;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
ALTER TABLE refresh_tokens ADD COLUMN last_used_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE refresh_tokens ADD COLUMN user_agent VARCHAR(255);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- Sessions move from browser storage to an HttpOnly cookie, so every existing session ends once.
UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE revoked_at IS NULL;
