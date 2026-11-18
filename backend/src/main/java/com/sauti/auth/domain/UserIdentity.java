package com.sauti.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A sign-in method from an external provider, linked by the provider's stable subject. Emails can
 * change or be reassigned at the provider; the subject cannot.
 */
@Entity
@Table(name = "user_identities")
public class UserIdentity {
    public static final String GOOGLE = "google";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false)
    private String providerSubject;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected UserIdentity() {
    }

    public UserIdentity(UUID userId, String provider, String providerSubject, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.provider = Objects.requireNonNull(provider, "provider");
        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("Provider subject is required");
        }
        this.providerSubject = providerSubject;
        this.createdAt = now;
    }

    public UUID userId() {
        return userId;
    }

    public String provider() {
        return provider;
    }
}
