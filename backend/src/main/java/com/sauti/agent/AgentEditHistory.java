package com.sauti.agent;

import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Records a version for every direct edit, inside the edit's own transaction, so the history and
 * the live configuration can never disagree. Publishes are recorded by {@link AgentSetupService}.
 */
@Component
class AgentEditHistory {
    private final AgentVersionService versions;

    AgentEditHistory(AgentVersionService versions) {
        this.versions = versions;
    }

    @EventListener
    void edited(AgentConfigurationChanged event) {
        if (event.change() == null || event.change().isBlank()) return;
        versions.recordEdit(event.tenantId(), event.agentId(), event.change(), actor());
    }

    /** The signed-in person's email, "API key" for API calls, or null for work the system did itself. */
    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails user)) return null;
        var name = user.getUsername();
        return name != null && name.startsWith("api-key:") ? "API key" : name;
    }
}
