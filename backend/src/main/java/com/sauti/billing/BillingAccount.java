package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "billing_accounts")
public class BillingAccount extends Auditable {
    private static final Set<String> STATUSES = Set.of("preview", "trialing", "active", "past_due", "suspended", "cancelled");
    private static final Set<String> MODES = Set.of("observe", "enforce");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tenantId;

    @Column(nullable = false, length = 20)
    private String status = "preview";

    @Column(nullable = false, length = 12)
    private String enforcementMode = "observe";

    @Column(nullable = false, length = 3)
    private String billingCurrency = "USD";

    @Column(precision = 19, scale = 4)
    private BigDecimal monthlySpendingLimit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lowBalanceThreshold = new BigDecimal("10.0000");

    protected BillingAccount() { }

    public BillingAccount(UUID tenantId) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
    }

    public void configure(String status, String enforcementMode, String billingCurrency,
                          BigDecimal monthlySpendingLimit, BigDecimal lowBalanceThreshold) {
        var normalizedStatus = normalized(status);
        var normalizedMode = normalized(enforcementMode);
        if (!STATUSES.contains(normalizedStatus)) throw new IllegalArgumentException("Unsupported billing status");
        if (!MODES.contains(normalizedMode)) throw new IllegalArgumentException("Unsupported billing enforcement mode");
        if (billingCurrency == null || !billingCurrency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Billing currency must be a three-letter ISO code");
        }
        if (monthlySpendingLimit != null && monthlySpendingLimit.signum() < 0) {
            throw new IllegalArgumentException("Monthly spending limit cannot be negative");
        }
        if (lowBalanceThreshold == null || lowBalanceThreshold.signum() < 0) {
            throw new IllegalArgumentException("Low balance threshold cannot be negative");
        }
        this.status = normalizedStatus;
        this.enforcementMode = normalizedMode;
        this.billingCurrency = billingCurrency.trim().toUpperCase(Locale.ROOT);
        this.monthlySpendingLimit = monthlySpendingLimit;
        this.lowBalanceThreshold = lowBalanceThreshold;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public String getEnforcementMode() { return enforcementMode; }
    public String getBillingCurrency() { return billingCurrency; }
    public BigDecimal getMonthlySpendingLimit() { return monthlySpendingLimit; }
    public BigDecimal getLowBalanceThreshold() { return lowBalanceThreshold; }
    public boolean isEnforced() { return "enforce".equals(enforcementMode); }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
