package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An immutable record of what an agent was told after one publish. */
@Entity
@Table(name = "agent_versions")
public class AgentVersion {
    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private int version;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(length = 200)
    private String note;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    protected AgentVersion() {
    }

    AgentVersion(UUID agentId, UUID tenantId, int version, String snapshotJson, String note, String publishedBy,
                 OffsetDateTime publishedAt) {
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.version = version;
        this.snapshotJson = snapshotJson;
        this.note = note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(200, note.trim().length()));
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public int getVersion() { return version; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getNote() { return note; }
    public String getPublishedBy() { return publishedBy; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
}
