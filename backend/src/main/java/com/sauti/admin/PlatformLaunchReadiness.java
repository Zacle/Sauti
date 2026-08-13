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
    @Column(length = 500) private String googleVerificationReference;
    private OffsetDateTime googleVerifiedAt;
    @Column(nullable = false) private boolean liveAcceptanceCompleted;
    @Column(length = 2000) private String liveAcceptanceEvidence;
    private OffsetDateTime liveAcceptedAt;
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
                String googleReference, boolean liveAcceptance, String liveEvidence,
                String notes, String actor, OffsetDateTime now) {
        this.securityReviewCompleted = security;
        this.privacyLegalReviewCompleted = privacyLegal;
        this.googleVerificationCompleted = googleVerification;
        this.googleVerificationReference = googleVerification
                ? normalize(googleReference) : null;
        this.googleVerifiedAt = googleVerification
                ? (this.googleVerifiedAt == null ? now : this.googleVerifiedAt) : null;
        this.liveAcceptanceCompleted = liveAcceptance;
        this.liveAcceptanceEvidence = liveAcceptance ? normalize(liveEvidence) : null;
        this.liveAcceptedAt = liveAcceptance
                ? (this.liveAcceptedAt == null ? now : this.liveAcceptedAt) : null;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.reviewedBy = actor;
        this.reviewedAt = now;
        this.generalAvailabilityApproved = false;
    }

    void approve() { this.generalAvailabilityApproved = true; }

    boolean isSecurityReviewCompleted() { return securityReviewCompleted; }
    boolean isPrivacyLegalReviewCompleted() { return privacyLegalReviewCompleted; }
    boolean isGoogleVerificationCompleted() { return googleVerificationCompleted; }
    String getGoogleVerificationReference() { return googleVerificationReference; }
    OffsetDateTime getGoogleVerifiedAt() { return googleVerifiedAt; }
    boolean isLiveAcceptanceCompleted() { return liveAcceptanceCompleted; }
    String getLiveAcceptanceEvidence() { return liveAcceptanceEvidence; }
    OffsetDateTime getLiveAcceptedAt() { return liveAcceptedAt; }
    boolean isGeneralAvailabilityApproved() { return generalAvailabilityApproved; }
    String getNotes() { return notes; }
    String getReviewedBy() { return reviewedBy; }
    OffsetDateTime getReviewedAt() { return reviewedAt; }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
