package com.sauti.agent;

import java.util.UUID;

public record AgentConfigurationChanged(UUID tenantId, UUID agentId) {
}
