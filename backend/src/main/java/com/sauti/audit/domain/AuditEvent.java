package com.sauti.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One entry in the activity log. Never updated after it is written. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    private UUID id;

    @Column(name = "tenant_id")
    private UUID workspaceId;

    private UUID userId;

    private UUID actorUserId;

    private UUID actorApiKeyId;

    @Column(nullable = false)
    private String actorLabel;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 32)
    private String targetType;

    @Column(length = 64)
    private String targetId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(length = 64)
    private String ipHash;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected AuditEvent() {
    }

    private AuditEvent(UUID workspaceId, UUID userId, UUID actorUserId, UUID actorApiKeyId, String actorLabel,
                       String action, String targetType, String targetId, String summary, String ipHash,
                       OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.actorUserId = actorUserId;
        this.actorApiKeyId = actorApiKeyId;
        this.actorLabel = limit(actorLabel == null || actorLabel.isBlank() ? "Sauti" : actorLabel, 255);
        this.action = limit(action, 64);
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = limit(summary, 500);
        this.ipHash = ipHash;
        this.createdAt = now;
    }

    public static AuditEvent inWorkspace(UUID workspaceId, UUID actorUserId, UUID actorApiKeyId, String actorLabel,
                                         String action, String targetType, String targetId, String summary,
                                         String ipHash, OffsetDateTime now) {
        return new AuditEvent(workspaceId, null, actorUserId, actorApiKeyId, actorLabel, action, targetType,
                targetId, summary, ipHash, now);
    }

    public static AuditEvent onAccount(UUID userId, String actorLabel, String action, String summary,
                                       String ipHash, OffsetDateTime now) {
        return new AuditEvent(null, userId, userId, null, actorLabel, action, "account", userId.toString(),
                summary, ipHash, now);
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID id() { return id; }
    public String actorLabel() { return actorLabel; }
    public String action() { return action; }
    public String summary() { return summary; }
    public OffsetDateTime createdAt() { return createdAt; }
}
