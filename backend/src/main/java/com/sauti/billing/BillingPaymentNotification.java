package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_payment_notifications", uniqueConstraints =
        @UniqueConstraint(columnNames = {"provider", "provider_payment_id"}))
public class BillingPaymentNotification extends Auditable {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "provider_payment_id", nullable = false, length = 100) private String providerPaymentId;
    @Column(nullable = false, length = 254) private String recipientEmail;
    @Column(nullable = false, length = 200) private String businessName;
    @Column(nullable = false, length = 100) private String purchaseDescription;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    private OffsetDateTime paidAt;
    @Column(length = 4) private String cardLast4;
    @Column(nullable = false) private boolean testMode;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private OffsetDateTime nextAttemptAt;
    @Column(length = 1000) private String lastError;
    private OffsetDateTime sentAt;

    protected BillingPaymentNotification() { }

    public BillingPaymentNotification(UUID tenantId, String provider, String providerPaymentId,
                                      String recipientEmail, String businessName,
                                      String purchaseDescription, BigDecimal amount, String currency,
                                      OffsetDateTime paidAt, String cardLast4, boolean testMode) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.provider = required(provider);
        this.providerPaymentId = required(providerPaymentId);
        this.recipientEmail = required(recipientEmail).toLowerCase(java.util.Locale.ROOT);
        this.businessName = required(businessName);
        this.purchaseDescription = required(purchaseDescription);
        this.amount = amount;
        this.currency = required(currency).toUpperCase(java.util.Locale.ROOT);
        this.paidAt = paidAt;
        this.cardLast4 = optional(cardLast4);
        this.testMode = testMode;
        this.status = "pending";
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
        var message = error == null || error.isBlank() ? "Payment confirmation email failed" : error.trim();
        lastError = message.substring(0, Math.min(1000, message.length()));
    }

    public UUID getTenantId() { return tenantId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getBusinessName() { return businessName; }
    public String getPurchaseDescription() { return purchaseDescription; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public OffsetDateTime getPaidAt() { return paidAt; }
    public String getCardLast4() { return cardLast4; }
    public boolean isTestMode() { return testMode; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Payment notification value is required");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
