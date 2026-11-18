package com.sauti.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** A person's place in a workspace. People are referenced by id only, so this module never loads accounts. */
@Entity
@Table(name = "workspace_memberships")
public class Membership {
    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected Membership() {
    }

    public Membership(UUID workspaceId, UUID userId, WorkspaceRole role, OffsetDateTime now) {
        this.id = new Key(workspaceId, userId);
        this.role = Objects.requireNonNull(role, "role");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID workspaceId() {
        return id.tenantId;
    }

    public UUID userId() {
        return id.userId;
    }

    public WorkspaceRole role() {
        return role;
    }

    public OffsetDateTime joinedAt() {
        return createdAt;
    }

    public boolean isOwner() {
        return role == WorkspaceRole.OWNER;
    }

    void assignRole(WorkspaceRole newRole, OffsetDateTime now) {
        this.role = Objects.requireNonNull(newRole, "role");
        this.updatedAt = now;
    }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "tenant_id", nullable = false)
        private UUID tenantId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected Key() {
        }

        public Key(UUID tenantId, UUID userId) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
            this.userId = Objects.requireNonNull(userId, "userId");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && tenantId.equals(key.tenantId) && userId.equals(key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, userId);
        }
    }
}
