package com.sauti.auth.infrastructure.security;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.tenant.application.ApiKeyService;
import com.sauti.tenant.domain.WorkspaceStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts {@code Authorization: Bearer sauti_sk_...} for software calling a workspace's API.
 * An unknown or revoked key gets 401 straight away instead of falling through as anonymous.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final String PREFIX = "Bearer " + ApiKeyService.KEY_START;

    private final ApiKeyService keys;

    public ApiKeyAuthenticationFilter(ApiKeyService keys) {
        this.keys = keys;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        return header == null || !header.startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var identity = keys.authenticate(request.getHeader("Authorization").substring("Bearer ".length())).orElse(null);
        if (identity == null) {
            SecurityErrors.write(response, 401, "invalid_api_key", "This API key is not valid or has been revoked");
            return;
        }
        if (identity.workspaceStatus() == WorkspaceStatus.SUSPENDED) {
            SecurityErrors.write(response, 423, "workspace_suspended", "This workspace is suspended. Contact Sauti support.");
            return;
        }
        var principal = AuthenticatedUser.apiKey(identity.keyId(), identity.name(), identity.workspaceId(), identity.permissions());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
