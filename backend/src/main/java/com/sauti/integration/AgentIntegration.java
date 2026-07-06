package com.sauti.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_integrations")
public class AgentIntegration {
    @Id private UUID id;
    private UUID tenantId;
    private UUID agentId;
    private String provider;
    private UUID connectionId;
    private boolean enabled;
    private String configuration;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected AgentIntegration() {}

    public AgentIntegration(UUID tenantId, UUID agentId, String provider) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.provider = provider;
        this.configuration = "{}";
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgentId() { return agentId; }
    public String getProvider() { return provider; }
    public UUID getConnectionId() { return connectionId; }
    public boolean isEnabled() { return enabled; }
    public String getConfiguration() { return configuration; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void configure(boolean enabled, UUID connectionId, String configuration) {
        this.enabled = enabled;
        this.connectionId = connectionId;
        this.configuration = configuration == null ? "{}" : configuration;
        this.updatedAt = OffsetDateTime.now();
    }

    public void disconnect() {
        this.connectionId = null;
        this.enabled = false;
        this.updatedAt = OffsetDateTime.now();
    }
}
