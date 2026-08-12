package com.sauti.admin;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "platform_launch_readiness")
class PlatformLaunchReadiness extends Auditable {
    static final String ID = "general_availability";

    @Id @Column(length = 40) private String id;
    @Column(nullable = false) private boolean securityReviewCompleted;
    @Column(nullable = false) private boolean privacyLegalReviewCompleted;
    @Column(nullable = false) private boolean googleVerificationCompleted;
    @Column(nullable = false) private boolean liveAcceptanceCompleted;
    @Column(nullable = false) private boolean generalAvailabilityApproved;
    @Column(length = 2000) private String notes;
    @Column(length = 320) private String reviewedBy;
    private OffsetDateTime reviewedAt;

    protected PlatformLaunchReadiness() { }

    PlatformLaunchReadiness(String actor, OffsetDateTime now) {
        this.id = ID;
        this.reviewedBy = actor;
        this.reviewedAt = now;
    }

    void review(boolean security, boolean privacyLegal, boolean googleVerification,
                boolean liveAcceptance, String notes, String actor, OffsetDateTime now) {
        this.securityReviewCompleted = security;
        this.privacyLegalReviewCompleted = privacyLegal;
        this.googleVerificationCompleted = googleVerification;
        this.liveAcceptanceCompleted = liveAcceptance;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.reviewedBy = actor;
        this.reviewedAt = now;
        this.generalAvailabilityApproved = false;
    }

    void approve() { this.generalAvailabilityApproved = true; }

    boolean isSecurityReviewCompleted() { return securityReviewCompleted; }
    boolean isPrivacyLegalReviewCompleted() { return privacyLegalReviewCompleted; }
    boolean isGoogleVerificationCompleted() { return googleVerificationCompleted; }
    boolean isLiveAcceptanceCompleted() { return liveAcceptanceCompleted; }
    boolean isGeneralAvailabilityApproved() { return generalAvailabilityApproved; }
    String getNotes() { return notes; }
    String getReviewedBy() { return reviewedBy; }
    OffsetDateTime getReviewedAt() { return reviewedAt; }
}
