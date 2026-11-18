package com.sauti.auth.infrastructure.security;

import com.sauti.tenant.domain.Permission;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Which permission each workspace route needs. One table, so access rules are reviewed in one
 * place. Reading needs {@link Permission#VIEW}; any change needs {@link Permission#CONFIGURE}
 * unless a narrower rule below says otherwise. New routes therefore default to Admin and Owner.
 *
 * <p>Member management routes only require VIEW here because the rules depend on who is being
 * changed (leaving yourself, Owner-only changes); the membership service enforces them.
 */
@Component
public class WorkspaceRoutePolicy {
    /** API areas that are not about one workspace. Everything else under /api/v1 needs a workspace. */
    private static final List<String> GLOBAL_PREFIXES = List.of(
            "/api/v1/auth", "/api/v1/me", "/api/v1/workspaces", "/api/v1/invitations",
            "/api/v1/public", "/api/v1/admin", "/api/v1/operator", "/api/v1/voices");

    public Permission requiredPermission(String method, String relativePath) {
        var verb = method.toUpperCase(Locale.ROOT);
        if (startsWith(relativePath == null ? "" : relativePath, "/activity")) return Permission.MANAGE_MEMBERS;
        if (startsWith(relativePath == null ? "" : relativePath, "/api-keys")) return Permission.MANAGE_MEMBERS;
        if (startsWith(relativePath == null ? "" : relativePath, "/export")) return Permission.OWN_WORKSPACE;
        if (verb.equals("GET") || verb.equals("HEAD") || verb.equals("OPTIONS")) return Permission.VIEW;
        var path = relativePath == null ? "" : relativePath;
        if (path.isEmpty() || path.equals("/")) {
            return verb.equals("DELETE") ? Permission.OWN_WORKSPACE : Permission.CONFIGURE;
        }
        if (startsWith(path, "/transfer-ownership")) return Permission.OWN_WORKSPACE;
        if (startsWith(path, "/members")) return Permission.VIEW;
        if (startsWith(path, "/invitations")) return Permission.MANAGE_MEMBERS;
        if (startsWith(path, "/api-keys")) return Permission.MANAGE_MEMBERS;
        if (startsWith(path, "/billing")) return Permission.MANAGE_BILLING;
        if (path.equals("/dashboard/socket-ticket")) return Permission.VIEW;
        if (startsWith(path, "/notifications")) return Permission.HANDLE_CONVERSATIONS;
        if (startsWith(path, "/whatsapp/conversations")) return Permission.HANDLE_CONVERSATIONS;
        return Permission.CONFIGURE;
    }

    public boolean requiresWorkspace(String path) {
        if (path == null || !path.startsWith("/api/v1/")) return false;
        return GLOBAL_PREFIXES.stream().noneMatch(prefix -> startsWith(path, prefix));
    }

    private static boolean startsWith(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
