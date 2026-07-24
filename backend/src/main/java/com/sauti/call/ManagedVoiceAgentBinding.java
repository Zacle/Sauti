package com.sauti.call;

import com.sauti.agent.Agent;
import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "managed_voice_agent_bindings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_managed_voice_agent_provider",
                columnNames = {"agent_id", "provider"}
        )
)
public class ManagedVoiceAgentBinding extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 255)
    private String externalAgentId;

    @Column(length = 255)
    private String externalVersionId;

    @Column(nullable = false, length = 64)
    private String blueprintHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String externalResourcesJson = "{}";

    @Column(nullable = false)
    private OffsetDateTime lastSyncedAt;

    protected ManagedVoiceAgentBinding() {
    }

    ManagedVoiceAgentBinding(
            Tenant tenant,
            Agent agent,
            String provider,
            String blueprintHash,
            ManagedVoiceAgentReference reference
    ) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.agent = agent;
        this.provider = provider;
        synchronize(blueprintHash, reference);
    }

    void synchronize(String blueprintHash, ManagedVoiceAgentReference reference) {
        this.externalAgentId = reference.externalAgentId();
        this.externalVersionId = reference.externalVersionId();
        this.externalResourcesJson = reference.externalResourcesJson();
        this.blueprintHash = blueprintHash;
        this.lastSyncedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalAgentId() {
        return externalAgentId;
    }

    public String getExternalVersionId() {
        return externalVersionId;
    }

    public String getBlueprintHash() {
        return blueprintHash;
    }

    public String getExternalResourcesJson() {
        return externalResourcesJson;
    }
}
