package com.sauti.auth;

import com.sauti.tenant.domain.Permission;
import com.sauti.tenant.domain.WorkspaceRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Who is making the current request: a signed-in person, or a workspace API key.
 *
 * <p>The access token identifies only the person; the workspace and role are added per request
 * from the URL ({@code /api/v1/workspaces/{id}/...}) after membership has been checked. An API key
 * is bound to one workspace and carries a fixed set of permissions instead of a role; it has no
 * {@code userId}.
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        boolean platformAdmin,
        UUID workspaceId,
        WorkspaceRole workspaceRole,
        UUID apiKeyId,
        Set<Permission> apiKeyPermissions
) implements UserDetails {
    public static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    public static AuthenticatedUser person(UUID userId, String email, boolean platformAdmin) {
        return new AuthenticatedUser(userId, email, platformAdmin, null, null, null, Set.of());
    }

    public static AuthenticatedUser apiKey(UUID keyId, String name, UUID workspaceId, Set<Permission> permissions) {
        return new AuthenticatedUser(null, "api-key:" + name, false, workspaceId, null, keyId, Set.copyOf(permissions));
    }

    public AuthenticatedUser inWorkspace(UUID workspaceId, WorkspaceRole role) {
        return new AuthenticatedUser(userId, email, platformAdmin, workspaceId, role, apiKeyId, apiKeyPermissions);
    }

    public boolean isApiKey() {
        return apiKeyId != null;
    }

    /** Whether this caller may do something that needs the given permission in its workspace. */
    public boolean grants(Permission permission) {
        if (isApiKey()) return apiKeyPermissions.contains(permission);
        return workspaceRole != null && workspaceRole.grants(permission);
    }

    /**
     * The workspace this request acts on. Feature code keeps calling this "tenant". Fails when the
     * request was not addressed to a workspace, rather than guessing one.
     */
    public UUID tenantId() {
        if (workspaceId == null) throw new WorkspaceRequiredException();
        return workspaceId;
    }

    public boolean hasWorkspace() {
        return workspaceId != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        var authorities = new ArrayList<GrantedAuthority>();
        if (platformAdmin) authorities.add(new SimpleGrantedAuthority("ROLE_" + PLATFORM_ADMIN_ROLE));
        if (workspaceRole != null) authorities.add(new SimpleGrantedAuthority("ROLE_WORKSPACE_" + workspaceRole.name()));
        if (isApiKey()) authorities.add(new SimpleGrantedAuthority("ROLE_API_KEY"));
        return List.copyOf(authorities);
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
