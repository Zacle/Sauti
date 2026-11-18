package com.sauti.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * One refresh token in a session. Each refresh rotates to a new token in the same family. The
 * family is the session: presenting a token that was rotated long ago means it was copied, and
 * the whole family is revoked.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    private OffsetDateTime rotatedAt;

    private OffsetDateTime revokedAt;

    private OffsetDateTime lastUsedAt;

    @Column(length = 255)
    private String userAgent;

    /** Started after a verified authenticator code; only these sessions carry platform-admin rights. */
    @Column(nullable = false)
    private boolean elevated;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(UUID userId, UUID familyId, String tokenHash, OffsetDateTime expiresAt,
                         String userAgent, boolean elevated, OffsetDateTime now) {
        this.elevated = elevated;
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.expiresAt = expiresAt;
        this.userAgent = truncate(userAgent);
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    /** Starts a new session (a new family) on sign-in. */
    public static RefreshToken startSession(UUID userId, String tokenHash, OffsetDateTime expiresAt,
                                            String userAgent, OffsetDateTime now) {
        return new RefreshToken(userId, UUID.randomUUID(), tokenHash, expiresAt, userAgent, false, now);
    }

    /** A platform-admin session, started only after a verified authenticator code. */
    public static RefreshToken startElevatedSession(UUID userId, String tokenHash, OffsetDateTime expiresAt,
                                                    String userAgent, OffsetDateTime now) {
        return new RefreshToken(userId, UUID.randomUUID(), tokenHash, expiresAt, userAgent, true, now);
    }

    /** The token that replaces this one when the session is refreshed. */
    public RefreshToken successor(String tokenHash, OffsetDateTime expiresAt, String userAgent, OffsetDateTime now) {
        return new RefreshToken(userId, familyId, tokenHash, expiresAt, userAgent, elevated, now);
    }

    /**
     * Decides what presenting this token means. A short grace period lets two browser tabs refresh
     * at the same moment without the second one being treated as theft.
     */
    public RefreshDecision decide(Duration rotationGrace, OffsetDateTime now) {
        if (revokedAt != null || !expiresAt.isAfter(now)) return RefreshDecision.REJECT;
        if (rotatedAt == null) return RefreshDecision.ROTATE;
        if (rotatedAt.plus(rotationGrace).isAfter(now)) return RefreshDecision.ROTATE_WITHIN_GRACE;
        return RefreshDecision.REUSE_DETECTED;
    }

    public void markRotated(OffsetDateTime now) {
        if (rotatedAt == null) rotatedAt = now;
        lastUsedAt = now;
    }

    public void revoke(OffsetDateTime now) {
        if (revokedAt == null) revokedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isElevated() {
        return elevated;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    public enum RefreshDecision {
        ROTATE,
        ROTATE_WITHIN_GRACE,
        /** Revoked or expired: refuse, but there is no sign of theft. */
        REJECT,
        /** An old, already-rotated token came back: assume it was copied and end the session. */
        REUSE_DETECTED
    }
}
