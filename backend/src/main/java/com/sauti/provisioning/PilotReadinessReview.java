package com.sauti.provisioning;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pilot_readiness_reviews")
public class PilotReadinessReview extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID tenantId;
    @Column(length = 160) private String supportContactName;
    @Column(length = 254) private String supportContactEmail;
    @Column(length = 40) private String supportContactPhone;
    @Column(length = 1000) private String launchNotes;
    @Column(nullable = false) private boolean launchApproved;
    @Column(length = 254) private String approvedBy;
    private OffsetDateTime approvedAt;

    protected PilotReadinessReview() { }

    public PilotReadinessReview(UUID tenantId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
    }

    public void update(String name, String email, String phone, String notes, boolean approved,
                       String actor, OffsetDateTime now) {
        this.supportContactName = optional(name);
        this.supportContactEmail = optional(email);
        this.supportContactPhone = optional(phone);
        this.launchNotes = optional(notes);
        this.launchApproved = approved;
        this.approvedBy = approved ? actor : null;
        this.approvedAt = approved ? now : null;
    }

    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID getTenantId() { return tenantId; }
    public String getSupportContactName() { return supportContactName; }
    public String getSupportContactEmail() { return supportContactEmail; }
    public String getSupportContactPhone() { return supportContactPhone; }
    public String getLaunchNotes() { return launchNotes; }
    public boolean isLaunchApproved() { return launchApproved; }
    public String getApprovedBy() { return approvedBy; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
}

