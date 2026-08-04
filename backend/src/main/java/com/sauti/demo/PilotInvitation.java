package com.sauti.demo;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pilot_invitations")
public class PilotInvitation extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID demoRequestId;
    @Column(nullable = false, length = 120) private String businessName;
    @Column(nullable = false, length = 120) private String contactName;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 2) private String countryCode;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private OffsetDateTime expiresAt;
    private OffsetDateTime acceptedAt;
    private OffsetDateTime revokedAt;
    @Column(nullable = false, length = 20) private String deliveryStatus;
    @Column(nullable = false) private int deliveryAttempts;
    private OffsetDateTime lastDeliveryAttemptAt;
    private OffsetDateTime sentAt;
    @Column(length = 200) private String lastDeliveryError;

    protected PilotInvitation() { }

    public PilotInvitation(DemoRequest request, String tokenHash, OffsetDateTime expiresAt) {
        this.id = UUID.randomUUID();
        this.demoRequestId = request.getId();
        this.businessName = request.getBusinessName();
        this.contactName = request.getContactName();
        this.email = request.getEmail();
        this.countryCode = request.getCountryCode();
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deliveryStatus = "pending";
    }

    public UUID getId() { return id; }
    public UUID getDemoRequestId() { return demoRequestId; }
    public String getBusinessName() { return businessName; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getCountryCode() { return countryCode; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public int getDeliveryAttempts() { return deliveryAttempts; }
    public OffsetDateTime getLastDeliveryAttemptAt() { return lastDeliveryAttemptAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public String getLastDeliveryError() { return lastDeliveryError; }

    public boolean isAvailableAt(OffsetDateTime now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void accept(OffsetDateTime now) {
        if (!isAvailableAt(now)) throw new IllegalStateException("Invitation is expired or already used");
        acceptedAt = now;
    }

    public void rotate(String nextTokenHash, OffsetDateTime nextExpiry) {
        if (acceptedAt != null) throw new IllegalStateException("An accepted invitation cannot be resent");
        tokenHash = nextTokenHash;
        expiresAt = nextExpiry;
        revokedAt = null;
        deliveryStatus = "pending";
        lastDeliveryError = null;
    }

    public void revoke(OffsetDateTime now) {
        if (acceptedAt != null) throw new IllegalStateException("An accepted invitation cannot be revoked");
        revokedAt = now;
    }

    public void recordSent(OffsetDateTime now) {
        deliveryAttempts++;
        lastDeliveryAttemptAt = now;
        sentAt = now;
        deliveryStatus = "sent";
        lastDeliveryError = null;
    }

    public void recordDeliveryFailure(OffsetDateTime now, String error) {
        deliveryAttempts++;
        lastDeliveryAttemptAt = now;
        deliveryStatus = "failed";
        lastDeliveryError = error == null ? "Email provider rejected the request" : error.substring(0, Math.min(200, error.length()));
    }
}
