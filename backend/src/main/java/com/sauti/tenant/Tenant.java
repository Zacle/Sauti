package com.sauti.tenant;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant extends Auditable {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private String plan = "trial";

    private OffsetDateTime planExpiresAt;

    @Column(nullable = false)
    private int monthlyMinutesLimit = 60;

    @Column(nullable = false)
    private int minutesUsedThisCycle = 0;

    private String lemonSqueezyCustomerId;

    private String webhookUrl;
    private String webhookSecret;

    @Column(nullable = false)
    private String status = "active";

    protected Tenant() {
    }

    public Tenant(String businessName, String email, String countryCode) {
        this.id = UUID.randomUUID();
        this.businessName = businessName;
        this.email = email.toLowerCase();
        this.countryCode = countryCode.toUpperCase();
    }

    public UUID getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getEmail() {
        return email;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPlan() {
        return plan;
    }

    public String getStatus() {
        return status;
    }

    public int getMonthlyMinutesLimit() {
        return monthlyMinutesLimit;
    }

    public int getMinutesUsedThisCycle() {
        return minutesUsedThisCycle;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void configureWebhook(String webhookUrl, String webhookSecret) {
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
    }

    public void addMinutesUsed(int minutes) {
        if (minutes > 0) {
            this.minutesUsedThisCycle += minutes;
        }
    }

    public void adjustMinutesUsed(int minutesDelta) {
        this.minutesUsedThisCycle = Math.max(0, this.minutesUsedThisCycle + minutesDelta);
    }
}
