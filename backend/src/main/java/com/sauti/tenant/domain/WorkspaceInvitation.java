package com.sauti.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** An emailed offer to join a workspace. Only a hash of the token is stored; the token itself travels in the email. */
@Entity
@Table(name = "workspace_invitations")
public class WorkspaceInvitation {
    public static final Duration LIFETIME = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    private UUID invitedByUserId;

    private UUID acceptedByUserId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    private OffsetDateTime acceptedAt;

    private OffsetDateTime revokedAt;

    protected WorkspaceInvitation() {
    }

    public WorkspaceInvitation(UUID workspaceId, String email, WorkspaceRole role, String tokenHash,
                               UUID invitedByUserId, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.email = normalizeEmail(email);
        this.role = Objects.requireNonNull(role, "role");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.invitedByUserId = invitedByUserId;
        this.createdAt = now;
        this.expiresAt = now.plus(LIFETIME);
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isPendingAt(OffsetDateTime now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isFor(String candidateEmail) {
        return candidateEmail != null && email.equals(candidateEmail.trim().toLowerCase(Locale.ROOT));
    }

    public void accept(UUID userId, OffsetDateTime now) {
        requirePending(now);
        this.acceptedByUserId = Objects.requireNonNull(userId, "userId");
        this.acceptedAt = now;
    }

    public void revoke(OffsetDateTime now) {
        if (acceptedAt != null) throw new MembershipRuleException("This invitation was already accepted");
        if (revokedAt == null) this.revokedAt = now;
    }

    /**
     * Resending issues a fresh token and a fresh expiry, so the old link stops working.
     * An expired invitation can be resent; an accepted or revoked one cannot.
     */
    public void reissue(String newTokenHash, OffsetDateTime now) {
        if (acceptedAt != null || revokedAt != null) throw new InvitationUnavailableException();
        this.tokenHash = Objects.requireNonNull(newTokenHash, "tokenHash");
        this.expiresAt = now.plus(LIFETIME);
    }

    private void requirePending(OffsetDateTime now) {
        if (!isPendingAt(now)) throw new InvitationUnavailableException();
    }

    public UUID id() { return id; }
    public UUID workspaceId() { return workspaceId; }
    public String email() { return email; }
    public WorkspaceRole role() { return role; }
    public UUID invitedByUserId() { return invitedByUserId; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime expiresAt() { return expiresAt; }
    public OffsetDateTime acceptedAt() { return acceptedAt; }
    public OffsetDateTime revokedAt() { return revokedAt; }
}
