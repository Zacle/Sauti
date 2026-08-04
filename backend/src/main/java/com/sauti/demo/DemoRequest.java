package com.sauti.demo;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "demo_requests")
public class DemoRequest extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String businessName;
    @Column(nullable = false, length = 120) private String contactName;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 2) private String countryCode;
    @Column(length = 40) private String phone;
    @Column(nullable = false, length = 80) private String industry;
    @Column(nullable = false, length = 40) private String monthlyCallVolume;
    @Column(nullable = false, length = 200) private String channels;
    @Column(nullable = false, length = 500) private String primaryUseCase;
    @Column(length = 1000) private String notes;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 254) private String assignedTo;
    @Column(length = 4000) private String internalNotes;
    private OffsetDateTime rejectedAt;
    @Column(length = 1000) private String rejectedReason;

    protected DemoRequest() { }

    public DemoRequest(String businessName, String contactName, String email, String countryCode,
                       String phone, String industry, String monthlyCallVolume, String channels,
                       String primaryUseCase, String notes) {
        this.id = UUID.randomUUID();
        this.businessName = required(businessName);
        this.contactName = required(contactName);
        this.email = required(email).toLowerCase();
        this.countryCode = required(countryCode).toUpperCase();
        this.phone = optional(phone);
        this.industry = required(industry);
        this.monthlyCallVolume = required(monthlyCallVolume);
        this.channels = required(channels);
        this.primaryUseCase = required(primaryUseCase);
        this.notes = optional(notes);
        this.status = "new";
    }

    public UUID getId() { return id; }
    public String getBusinessName() { return businessName; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getCountryCode() { return countryCode; }
    public String getPhone() { return phone; }
    public String getIndustry() { return industry; }
    public String getMonthlyCallVolume() { return monthlyCallVolume; }
    public String getChannels() { return channels; }
    public String getPrimaryUseCase() { return primaryUseCase; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public String getAssignedTo() { return assignedTo; }
    public String getInternalNotes() { return internalNotes; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
    public String getRejectedReason() { return rejectedReason; }

    public void markInvited() {
        if (!("new".equals(status) || "approved".equals(status))) {
            throw new IllegalStateException("Demo request cannot be invited in its current state");
        }
        status = "invited";
    }

    public void markInvitationRevoked() {
        if (!"invited".equals(status)) throw new IllegalStateException("Demo request has no active invitation");
        status = "approved";
    }

    public void reject(String reason, OffsetDateTime now) {
        if ("activated".equals(status)) throw new IllegalStateException("An activated pilot cannot be rejected");
        if ("rejected".equals(status)) throw new IllegalStateException("Demo request has already been rejected");
        status = "rejected";
        rejectedReason = required(reason);
        rejectedAt = now;
    }

    public void updateOperations(String assignedTo, String internalNotes) {
        this.assignedTo = optional(assignedTo);
        this.internalNotes = optional(internalNotes);
    }

    public void markActivated() {
        if (!"invited".equals(status)) throw new IllegalStateException("Demo request is not awaiting activation");
        status = "activated";
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Demo request value is required");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
