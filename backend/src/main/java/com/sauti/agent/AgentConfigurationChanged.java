package com.sauti.agent;

import java.util.UUID;

/**
 * Something the live assistant is told about an agent changed, so it must be refreshed.
 *
 * @param change what the owner changed, in a few words ("Changed tool book_slot"), when the edit was
 *               made directly rather than by publishing; it is then recorded as a new version. Null
 *               for publishes (they record their own version) and for changes the system made itself.
 */
public record AgentConfigurationChanged(UUID tenantId, UUID agentId, String change) {
    public AgentConfigurationChanged(UUID tenantId, UUID agentId) {
        this(tenantId, agentId, null);
    }
}
