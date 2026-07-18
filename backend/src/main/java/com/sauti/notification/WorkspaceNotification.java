package com.sauti.notification;

import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspace_notifications")
public class WorkspaceNotification extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 300)
    private String href;

    @Column(nullable = false, length = 40)
    private String resourceType;

    private UUID resourceId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload = "{}";

    private OffsetDateTime readAt;

    protected WorkspaceNotification() {
    }

    public WorkspaceNotification(
            Tenant tenant,
            String type,
            String title,
            String message,
            String href,
            String resourceType,
            UUID resourceId,
            String payload
    ) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.type = type;
        this.title = title;
        this.message = message;
        this.href = href;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload == null || payload.isBlank() ? "{}" : payload;
    }

    public void markRead() {
        if (readAt == null) readAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getHref() { return href; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getPayload() { return payload; }
    public OffsetDateTime getReadAt() { return readAt; }
}
