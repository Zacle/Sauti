package com.sauti.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "platform_admin_audit_events")
public class PlatformAdminAuditEvent {
    @Id private UUID id;
    @Column(nullable = false, length = 254) private String actorEmail;
    @Column(nullable = false, length = 80) private String action;
    @Column(nullable = false, length = 80) private String resourceType;
    @Column(nullable = false, length = 100) private String resourceId;
    @Column(nullable = false, length = 500) private String summary;
    @Column(nullable = false) private OffsetDateTime createdAt;

    protected PlatformAdminAuditEvent() { }

    public PlatformAdminAuditEvent(String actorEmail, String action, String resourceType,
                                   String resourceId, String summary) {
        this.id = UUID.randomUUID();
        this.actorEmail = required(actorEmail);
        this.action = required(action);
        this.resourceType = required(resourceType);
        this.resourceId = required(resourceId);
        this.summary = required(summary);
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getSummary() { return summary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Audit value is required");
        return value.trim();
    }
}
