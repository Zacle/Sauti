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

    public boolean isAvailableAt(OffsetDateTime now) {
        return acceptedAt == null && expiresAt.isAfter(now);
    }

    public void accept(OffsetDateTime now) {
        if (!isAvailableAt(now)) throw new IllegalStateException("Invitation is expired or already used");
        acceptedAt = now;
    }
}
