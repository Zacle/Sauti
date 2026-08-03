package com.sauti.demo;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "demo_requests")
public class DemoRequest extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String businessName;
    @Column(nullable = false, length = 120) private String contactName;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 2) private String countryCode;
    @Column(length = 40) private String phone;
    @Column(nullable = false, length = 80) private String industry;
    @Column(nullable = false, length = 40) private String monthlyCallVolume;
    @Column(nullable = false, length = 200) private String channels;
    @Column(nullable = false, length = 500) private String primaryUseCase;
    @Column(length = 1000) private String notes;
    @Column(nullable = false, length = 20) private String status;

    protected DemoRequest() { }

    public DemoRequest(String businessName, String contactName, String email, String countryCode,
                       String phone, String industry, String monthlyCallVolume, String channels,
                       String primaryUseCase, String notes) {
        this.id = UUID.randomUUID();
        this.businessName = required(businessName);
        this.contactName = required(contactName);
        this.email = required(email).toLowerCase();
        this.countryCode = required(countryCode).toUpperCase();
        this.phone = optional(phone);
        this.industry = required(industry);
        this.monthlyCallVolume = required(monthlyCallVolume);
        this.channels = required(channels);
        this.primaryUseCase = required(primaryUseCase);
        this.notes = optional(notes);
        this.status = "new";
    }

    public UUID getId() { return id; }
    public String getBusinessName() { return businessName; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getCountryCode() { return countryCode; }
    public String getPhone() { return phone; }
    public String getIndustry() { return industry; }
    public String getMonthlyCallVolume() { return monthlyCallVolume; }
    public String getChannels() { return channels; }
    public String getPrimaryUseCase() { return primaryUseCase; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Demo request value is required");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
