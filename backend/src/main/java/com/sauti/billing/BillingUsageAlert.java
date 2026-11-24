package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * A workspace reached 80% or 100% of its call minutes or of its outbound messages. Stored once per
 * kind, cycle, allowance and threshold, then emailed to the workspace owner with retries.
 *
 * <p>Status: {@code pending} or {@code retrying} until the email goes out, then {@code sent}; {@code failed}
 * after the last attempt. {@code skipped} marks an 80% alert that 100% overtook in the same check, so
 * the owner gets one email, not two.
 */
@Entity
@Table(name = "billing_usage_alerts")
public class BillingUsageAlert extends Auditable {
    public static final String MINUTES = "minutes";
    public static final String MESSAGES = "messages";

    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 20) private String kind;
    @Column(nullable = false) private OffsetDateTime cycleStartsAt;
    private OffsetDateTime cycleEndsAt;
    @Column(nullable = false) private int includedAmount;
    @Column(nullable = false) private int thresholdPercent;
    @Column(nullable = false) private int usedAmount;
    @Column(nullable = false) private boolean trial;
    @Column(nullable = false, length = 254) private String recipientEmail;
    @Column(nullable = false, length = 200) private String businessName;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private OffsetDateTime nextAttemptAt;
    @Column(length = 1000) private String lastError;
    private OffsetDateTime sentAt;

    protected BillingUsageAlert() { }

    BillingUsageAlert(UUID tenantId, String kind, WorkspaceUsageService.Usage usage, int thresholdPercent,
                      String recipientEmail, String businessName, boolean skipped) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.kind = MESSAGES.equals(kind) ? MESSAGES : MINUTES;
        this.cycleStartsAt = usage.cycleStartsAt();
        this.cycleEndsAt = usage.cycleEndsAt();
        this.includedAmount = messages() ? usage.includedMessages() : usage.includedMinutes();
        this.usedAmount = messages() ? usage.usedMessages() : usage.usedMinutes();
        this.thresholdPercent = thresholdPercent;
        this.trial = usage.trial();
        this.recipientEmail = recipientEmail.trim().toLowerCase(Locale.ROOT);
        this.businessName = businessName.trim();
        this.status = skipped ? "skipped" : "pending";
        this.nextAttemptAt = OffsetDateTime.now();
    }

    public void sent() {
        status = "sent";
        sentAt = OffsetDateTime.now();
        lastError = null;
    }

    public void retry(String error) {
        attempts++;
        status = attempts >= 8 ? "failed" : "retrying";
        nextAttemptAt = OffsetDateTime.now().plusSeconds(Math.min(3600, 5L << Math.min(attempts, 9)));
        var message = error == null || error.isBlank() ? "Usage alert email failed" : error.trim();
        lastError = message.substring(0, Math.min(1000, message.length()));
    }

    public boolean messages() { return MESSAGES.equals(kind); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getKind() { return kind; }
    public OffsetDateTime getCycleStartsAt() { return cycleStartsAt; }
    public OffsetDateTime getCycleEndsAt() { return cycleEndsAt; }
    public int getIncludedAmount() { return includedAmount; }
    public int getThresholdPercent() { return thresholdPercent; }
    public int getUsedAmount() { return usedAmount; }
    public boolean isTrial() { return trial; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getBusinessName() { return businessName; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
}
