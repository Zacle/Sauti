package com.sauti.provisioning;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "pilot_provisioning_policies")
public class PilotProvisioningPolicy extends Auditable {
    private static final Set<String> STATUSES = Set.of("pending", "approved", "suspended");
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID tenantId;
    @Column(nullable = false, length = 20) private String status = "pending";
    @Column(nullable = false, length = 3) private String currency = "USD";
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal monthlyBudget = BigDecimal.ZERO;
    @Column(nullable = false) private boolean phoneNumbersApproved;
    @Column(nullable = false) private boolean liveCallingApproved;
    @Column(nullable = false) private boolean smsApproved;
    @Column(nullable = false) private boolean whatsappApproved;
    @Column(length = 254) private String approvedBy;
    private OffsetDateTime approvedAt;
    @Column(length = 1000) private String notes;

    protected PilotProvisioningPolicy() { }
    public PilotProvisioningPolicy(UUID tenantId) { this.id = UUID.randomUUID(); this.tenantId = tenantId; }

    public void configure(String status, String currency, BigDecimal monthlyBudget,
                          boolean phoneNumbersApproved, boolean liveCallingApproved,
                          boolean smsApproved, boolean whatsappApproved, String notes,
                          String actor, OffsetDateTime now) {
        var normalizedStatus = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalizedStatus)) throw new IllegalArgumentException("Unsupported pilot provisioning status");
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) throw new IllegalArgumentException("Budget currency must be a three-letter ISO code");
        if (monthlyBudget == null || monthlyBudget.signum() < 0) throw new IllegalArgumentException("Monthly pilot budget cannot be negative");
        this.status = normalizedStatus;
        this.currency = currency.trim().toUpperCase(Locale.ROOT);
        this.monthlyBudget = monthlyBudget;
        this.phoneNumbersApproved = phoneNumbersApproved;
        this.liveCallingApproved = liveCallingApproved;
        this.smsApproved = smsApproved;
        this.whatsappApproved = whatsappApproved;
        this.notes = optional(notes);
        if ("approved".equals(normalizedStatus)) { this.approvedBy = actor; this.approvedAt = now; }
        else { this.approvedBy = null; this.approvedAt = null; }
    }

    public boolean permits(String capability) {
        if (!"approved".equals(status)) return false;
        return switch (capability) {
            case "phone_numbers" -> phoneNumbersApproved;
            case "live_calling" -> liveCallingApproved;
            case "sms" -> smsApproved;
            case "whatsapp" -> whatsappApproved;
            default -> false;
        };
    }

    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; }
    public String getStatus() { return status; } public String getCurrency() { return currency; }
    public BigDecimal getMonthlyBudget() { return monthlyBudget; }
    public boolean isPhoneNumbersApproved() { return phoneNumbersApproved; }
    public boolean isLiveCallingApproved() { return liveCallingApproved; }
    public boolean isSmsApproved() { return smsApproved; }
    public boolean isWhatsappApproved() { return whatsappApproved; }
    public String getApprovedBy() { return approvedBy; } public OffsetDateTime getApprovedAt() { return approvedAt; }
    public String getNotes() { return notes; }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
