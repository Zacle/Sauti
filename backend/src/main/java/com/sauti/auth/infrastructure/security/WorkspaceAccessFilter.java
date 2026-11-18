package com.sauti.auth.infrastructure.security;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.tenant.application.WorkspaceAccessService;
import com.sauti.tenant.domain.WorkspaceStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after the access token is verified. For a request addressed to a workspace it checks, in
 * order: the person is a member (otherwise 404, so ids cannot be probed), the workspace is open
 * (suspended is 423), and the member's role grants the route's permission (otherwise 403). It then
 * attaches the workspace and role to the principal for the controller.
 *
 * <p>A signed-in request to a workspace feature without a workspace in the URL is rejected rather
 * than routed to a guessed workspace.
 */
public class WorkspaceAccessFilter extends OncePerRequestFilter {
    private final WorkspaceAccessService access;
    private final WorkspaceRoutePolicy routes;

    public WorkspaceAccessFilter(WorkspaceAccessService access, WorkspaceRoutePolicy routes) {
        this.access = access;
        this.routes = routes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var person = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user : null;
        var workspaceId = (UUID) request.getAttribute(WorkspacePathFilter.WORKSPACE_ID);

        if (person != null && person.isApiKey()) {
            checkApiKey(request, response, chain, person, workspaceId);
            return;
        }

        if (workspaceId == null) {
            if (person != null && routes.requiresWorkspace(request.getRequestURI().substring(request.getContextPath().length()))) {
                SecurityErrors.write(response, 400, "workspace_required",
                        "Choose a workspace: call this under /api/v1/workspaces/{workspaceId}/");
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (person == null) {
            chain.doFilter(request, response);
            return;
        }

        var resolved = access.resolve(person.userId(), workspaceId).orElse(null);
        if (resolved == null || resolved.status() == WorkspaceStatus.CLOSED) {
            SecurityErrors.write(response, 404, "not_found", "Workspace not found");
            return;
        }
        if (resolved.status() == WorkspaceStatus.SUSPENDED) {
            SecurityErrors.write(response, 423, "workspace_suspended",
                    "This workspace is suspended. Contact Sauti support.");
            return;
        }
        var relativePath = (String) request.getAttribute(WorkspacePathFilter.WORKSPACE_RELATIVE_PATH);
        var permission = routes.requiredPermission(request.getMethod(), relativePath);
        if (!resolved.role().grants(permission)) {
            SecurityErrors.write(response, 403, "forbidden", "Your role in this workspace does not allow this");
            return;
        }
        var scoped = person.inWorkspace(workspaceId, resolved.role());
        var scopedAuthentication = new UsernamePasswordAuthenticationToken(scoped, null, scoped.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(scopedAuthentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    /**
     * API keys may only call workspace features in their own workspace, never account, member,
     * invitation, key or ownership routes, and only within the permissions the key was given.
     */
    private void checkApiKey(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                             AuthenticatedUser key, UUID workspaceId) throws ServletException, IOException {
        var relativePath = (String) request.getAttribute(WorkspacePathFilter.WORKSPACE_RELATIVE_PATH);
        if (workspaceId == null || relativePath == null || relativePath.isEmpty() || relativePath.equals("/")
                || WorkspacePathFilter.WORKSPACE_OWN_ROUTES.contains(WorkspacePathFilter.firstSegment(relativePath))) {
            SecurityErrors.write(response, 403, "forbidden",
                    "API keys can only call workspace features under /api/v1/workspaces/{workspaceId}/");
            return;
        }
        if (!workspaceId.equals(key.workspaceId())) {
            SecurityErrors.write(response, 404, "not_found", "Workspace not found");
            return;
        }
        if (!key.grants(routes.requiredPermission(request.getMethod(), relativePath))) {
            SecurityErrors.write(response, 403, "forbidden", "This API key does not allow this");
            return;
        }
        chain.doFilter(request, response);
    }
}
