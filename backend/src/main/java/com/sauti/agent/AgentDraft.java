package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Unpublished edits to an agent. Live calls never read it; publishing applies it. */
@Entity
@Table(name = "agent_drafts")
public class AgentDraft {
    @Id
    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "setup_json", nullable = false, columnDefinition = "TEXT")
    private String setupJson;

    @Column(name = "saved_by")
    private String savedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AgentDraft() {
    }

    AgentDraft(UUID agentId, UUID tenantId) {
        this.agentId = agentId;
        this.tenantId = tenantId;
    }

    void replace(String setupJson, String savedBy, OffsetDateTime now) {
        this.setupJson = setupJson;
        this.savedBy = savedBy;
        this.updatedAt = now;
    }

    public UUID getAgentId() { return agentId; }
    public UUID getTenantId() { return tenantId; }
    public String getSetupJson() { return setupJson; }
    public String getSavedBy() { return savedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
