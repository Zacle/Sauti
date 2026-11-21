package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * What an agent was told after one publish or direct edit. Publishes are never changed; a burst of
 * direct edits by one person is folded into a single edit version (see {@link #absorb}).
 */
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

    /** "publish" or "edit". */
    @Column(nullable = false, length = 20)
    private String kind = PUBLISH;

    @Column(name = "parts_json", columnDefinition = "TEXT")
    private String partsJson;

    static final String PUBLISH = "publish";
    static final String EDIT = "edit";
    private static final int NOTE_LENGTH = 200;

    protected AgentVersion() {
    }

    AgentVersion(UUID agentId, UUID tenantId, int version, String kind, String snapshotJson, String partsJson,
                 String note, String publishedBy, OffsetDateTime publishedAt) {
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.version = version;
        this.kind = kind;
        this.snapshotJson = snapshotJson;
        this.partsJson = partsJson;
        this.note = shorten(note);
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    /** Folds a further direct edit into this edit version: it now holds the newest state. */
    void absorb(String snapshotJson, String partsJson, String change, OffsetDateTime at) {
        this.snapshotJson = snapshotJson;
        this.partsJson = partsJson;
        this.publishedAt = at;
        if (change == null || change.isBlank() || (note != null && note.contains(change.trim()))) return;
        this.note = shorten(note == null ? change : note + "; " + change.trim());
    }

    /** Whether an edit by {@code actor} at {@code at} belongs in this version rather than a new one. */
    boolean absorbs(String actor, OffsetDateTime at, java.time.Duration window) {
        return EDIT.equals(kind) && java.util.Objects.equals(publishedBy, actor)
                && publishedAt.plus(window).isAfter(at);
    }

    private static String shorten(String note) {
        if (note == null || note.isBlank()) return null;
        var trimmed = note.trim();
        return trimmed.length() <= NOTE_LENGTH ? trimmed : trimmed.substring(0, NOTE_LENGTH - 1) + "…";
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public int getVersion() { return version; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getNote() { return note; }
    public String getPublishedBy() { return publishedBy; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public String getKind() { return kind; }
    public String getPartsJson() { return partsJson; }
}
