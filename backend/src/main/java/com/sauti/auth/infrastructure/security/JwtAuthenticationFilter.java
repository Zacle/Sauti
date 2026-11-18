package com.sauti.auth.infrastructure.security;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.auth.application.port.AccessTokens;
import com.sauti.tenant.application.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Turns a valid bearer access token into the signed-in person. Invalid tokens leave the request anonymous. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AccessTokens accessTokens;

    public JwtAuthenticationFilter(AccessTokens accessTokens) {
        this.accessTokens = accessTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && !header.startsWith("Bearer " + ApiKeyService.KEY_START)) {
            try {
                var claims = accessTokens.verify(header.substring(7));
                var person = AuthenticatedUser.person(claims.userId(), claims.email(), claims.platformAdmin());
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(person, null, person.getAuthorities()));
                SecurityContextHolder.setContext(context);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
