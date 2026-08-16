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

    @Column(nullable = false)
    private int conversationRetentionDays = 90;

    @Column(nullable = false)
    private int recordingRetentionDays = 30;

    @Column(nullable = false, length = 100)
    private String timezone = "UTC";

    @Column(nullable = false)
    private int defaultBookingDurationMinutes = 60;

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

    public int getConversationRetentionDays() {
        return conversationRetentionDays;
    }

    public int getRecordingRetentionDays() {
        return recordingRetentionDays;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getDefaultBookingDurationMinutes() {
        return defaultBookingDurationMinutes;
    }

    public void configureWorkspaceProfile(String businessName, String timezone, int defaultBookingDurationMinutes) {
        var normalizedName = businessName == null ? "" : businessName.trim();
        if (normalizedName.length() < 2 || normalizedName.length() > 120) {
            throw new IllegalArgumentException("Business name must contain between 2 and 120 characters");
        }
        String normalizedTimezone;
        try {
            normalizedTimezone = java.time.ZoneId.of(timezone == null ? "" : timezone.trim()).getId();
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Select a valid IANA timezone");
        }
        if (defaultBookingDurationMinutes < 5 || defaultBookingDurationMinutes > 480) {
            throw new IllegalArgumentException("Default booking duration must be between 5 and 480 minutes");
        }
        this.businessName = normalizedName;
        this.timezone = normalizedTimezone;
        this.defaultBookingDurationMinutes = defaultBookingDurationMinutes;
    }

    public void configurePrivacyRetention(int conversationRetentionDays, int recordingRetentionDays) {
        if (!java.util.Set.of(30, 90, 180, 365).contains(conversationRetentionDays)) {
            throw new IllegalArgumentException("Conversation retention must be 30, 90, 180, or 365 days");
        }
        if (!java.util.Set.of(7, 30, 90).contains(recordingRetentionDays)) {
            throw new IllegalArgumentException("Recording retention must be 7, 30, or 90 days");
        }
        if (recordingRetentionDays > conversationRetentionDays) {
            throw new IllegalArgumentException("Recording retention cannot exceed conversation retention");
        }
        this.conversationRetentionDays = conversationRetentionDays;
        this.recordingRetentionDays = recordingRetentionDays;
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

    public void applyBillingSubscription(String plan, int monthlyMinutesLimit,
                                         OffsetDateTime planExpiresAt, String customerId) {
        if (plan == null || plan.isBlank()) throw new IllegalArgumentException("Subscription plan is required");
        if (monthlyMinutesLimit <= 0) throw new IllegalArgumentException("Subscription minutes must be positive");
        this.plan = plan.trim().toLowerCase(java.util.Locale.ROOT);
        this.monthlyMinutesLimit = monthlyMinutesLimit;
        this.planExpiresAt = planExpiresAt;
        this.lemonSqueezyCustomerId = customerId == null || customerId.isBlank() ? null : customerId.trim();
    }
}
