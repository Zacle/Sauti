package com.sauti.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A Sauti platform administrator. Being one requires this row and, on every admin sign-in, a
 * code from the authenticator app registered here. The secret is stored encrypted.
 */
@Entity
@Table(name = "platform_admins")
public class PlatformAdmin {
    @Id
    private UUID userId;

    @Column(length = 512)
    private String totpSecretEncrypted;

    private OffsetDateTime totpConfirmedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected PlatformAdmin() {
    }

    public PlatformAdmin(UUID userId, OffsetDateTime now) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.createdAt = now;
    }

    public boolean hasAuthenticator() {
        return totpConfirmedAt != null && totpSecretEncrypted != null;
    }

    /** Starts or restarts setup. Refused once an authenticator is confirmed, so it cannot be swapped silently. */
    public void beginAuthenticatorSetup(String encryptedSecret) {
        if (hasAuthenticator()) throw new IllegalStateException("An authenticator app is already set up");
        this.totpSecretEncrypted = Objects.requireNonNull(encryptedSecret, "encryptedSecret");
    }

    public void confirmAuthenticator(OffsetDateTime now) {
        if (totpSecretEncrypted == null) throw new IllegalStateException("Set up an authenticator app first");
        if (totpConfirmedAt == null) totpConfirmedAt = now;
    }

    public UUID userId() {
        return userId;
    }

    public String totpSecretEncrypted() {
        return totpSecretEncrypted;
    }
}
