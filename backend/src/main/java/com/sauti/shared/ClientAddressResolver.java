package com.sauti.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the network identity after the servlet container has applied its
 * trusted-proxy policy. Application code must not parse X-Forwarded-For
 * directly because a client can prepend arbitrary addresses to that header.
 */
@Component
public class ClientAddressResolver {
    public String resolve(HttpServletRequest request) {
        var address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address.trim();
    }
}
