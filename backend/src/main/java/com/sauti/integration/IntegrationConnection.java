package com.sauti.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "integration_connections")
public class IntegrationConnection {
    @Id private UUID id;
    private UUID tenantId;
    private String provider;
    private String displayName;
    private String status;
    private String encryptedCredentials;
    private String configuration;
    private String externalAccountId;
    private OffsetDateTime lastTestedAt;
    private String lastError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected IntegrationConnection() {}

    public IntegrationConnection(UUID tenantId, String provider, String displayName,
                                 String encryptedCredentials, String configuration) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.provider = provider;
        this.displayName = displayName;
        this.status = "connected";
        this.encryptedCredentials = encryptedCredentials;
        this.configuration = configuration;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getProvider() { return provider; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public String getEncryptedCredentials() { return encryptedCredentials; }
    public String getConfiguration() { return configuration; }
    public String getExternalAccountId() { return externalAccountId; }
    public OffsetDateTime getLastTestedAt() { return lastTestedAt; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void update(String displayName, String encryptedCredentials, String configuration) {
        if (displayName != null && !displayName.isBlank()) this.displayName = displayName.trim();
        if (encryptedCredentials != null) this.encryptedCredentials = encryptedCredentials;
        if (configuration != null) this.configuration = configuration;
        this.status = "connected";
        this.updatedAt = OffsetDateTime.now();
    }

    public void testSucceeded() {
        status = "connected";
        lastTestedAt = OffsetDateTime.now();
        lastError = null;
        updatedAt = lastTestedAt;
    }

    public void testFailed(String error) {
        status = "error";
        lastTestedAt = OffsetDateTime.now();
        lastError = error;
        updatedAt = lastTestedAt;
    }
}
