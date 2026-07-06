package com.sauti.tool;

import com.sauti.tenant.Tenant;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "calendar_credentials")
public class CalendarCredential {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    private String provider;
    private String accessToken;
    private String refreshToken;
    private OffsetDateTime tokenExpiry;
    private String externalId;
    private OffsetDateTime createdAt;

    protected CalendarCredential() {
    }

    public CalendarCredential(
            Tenant tenant,
            String provider,
            String accessToken,
            String refreshToken,
            OffsetDateTime tokenExpiry,
            String externalId
    ) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.provider = provider;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenExpiry = tokenExpiry;
        this.externalId = externalId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getProvider() {
        return provider;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public OffsetDateTime getTokenExpiry() {
        return tokenExpiry;
    }

    public String getExternalId() {
        return externalId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void updateTokens(String accessToken, String refreshToken, OffsetDateTime tokenExpiry) {
        this.accessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            this.refreshToken = refreshToken;
        }
        this.tokenExpiry = tokenExpiry;
    }

    public void selectCalendar(String externalId) {
        this.externalId = externalId;
    }
}
