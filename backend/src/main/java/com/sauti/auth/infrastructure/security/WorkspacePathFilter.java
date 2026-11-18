package com.sauti.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Reads the workspace from {@code /api/v1/workspaces/{id}/...} before Spring Security runs.
 *
 * <p>Feature routes are served under the workspace prefix without each controller knowing about
 * it: {@code /api/v1/workspaces/{id}/agents} is handed on as {@code /api/v1/agents}, with the
 * workspace id kept as a request attribute. Routes that belong to the workspace itself
 * (members, invitations, ownership) keep their full path. {@link WorkspaceAccessFilter} then
 * checks membership, status and role.
 */
public class WorkspacePathFilter extends OncePerRequestFilter {
    public static final String WORKSPACE_ID = WorkspacePathFilter.class.getName() + ".workspaceId";
    public static final String WORKSPACE_RELATIVE_PATH = WorkspacePathFilter.class.getName() + ".relativePath";

    private static final Pattern WORKSPACE_PATH =
            Pattern.compile("^/api/v1/workspaces/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(/.*)?$");
    /** Sub-paths served by the workspace controller itself rather than by a feature controller. */
    static final Set<String> WORKSPACE_OWN_ROUTES = Set.of("members", "invitations", "transfer-ownership", "api-keys", "export");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var contextPath = request.getContextPath();
        var path = request.getRequestURI().substring(contextPath.length());
        var matcher = WORKSPACE_PATH.matcher(path);
        if (!matcher.matches()) {
            chain.doFilter(request, response);
            return;
        }
        var relative = matcher.group(2) == null ? "" : matcher.group(2);
        request.setAttribute(WORKSPACE_ID, UUID.fromString(matcher.group(1)));
        request.setAttribute(WORKSPACE_RELATIVE_PATH, relative);
        if (relative.isEmpty() || relative.equals("/") || WORKSPACE_OWN_ROUTES.contains(firstSegment(relative))) {
            chain.doFilter(request, response);
            return;
        }
        request.removeAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
        request.removeAttribute(UrlPathHelper.PATH_ATTRIBUTE);
        chain.doFilter(new FeaturePathRequest(request, contextPath, "/api/v1" + relative), response);
    }

    static String firstSegment(String relativePath) {
        var trimmed = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        var slash = trimmed.indexOf('/');
        return slash < 0 ? trimmed : trimmed.substring(0, slash);
    }

    /** The same request, addressed to the feature route. */
    private static final class FeaturePathRequest extends HttpServletRequestWrapper {
        private final String contextPath;
        private final String path;

        FeaturePathRequest(HttpServletRequest request, String contextPath, String path) {
            super(request);
            this.contextPath = contextPath;
            this.path = path;
        }

        @Override
        public String getRequestURI() {
            return contextPath + path;
        }

        @Override
        public StringBuffer getRequestURL() {
            var original = super.getRequestURL().toString();
            var schemeAndHost = original.substring(0, original.length() - super.getRequestURI().length());
            return new StringBuffer(schemeAndHost).append(getRequestURI());
        }

        @Override
        public String getServletPath() {
            return path;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
