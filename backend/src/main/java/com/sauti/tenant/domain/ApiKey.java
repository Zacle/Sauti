package com.sauti.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A credential for software calling Sauti on behalf of one workspace. It belongs to the workspace,
 * not to the person who created it, so it keeps working when that person leaves.
 */
@Entity
@Table(name = "workspace_api_keys")
public class ApiKey {
    /** How stale "last used" may get before it is written again, to avoid a write on every request. */
    public static final Duration LAST_USED_PRECISION = Duration.ofMinutes(5);

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 16)
    private String prefix;

    @Column(nullable = false, length = 64)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApiKeyAccess access;

    private UUID createdByUserId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime lastUsedAt;

    private OffsetDateTime revokedAt;

    protected ApiKey() {
    }

    public ApiKey(UUID workspaceId, String name, String prefix, String keyHash, ApiKeyAccess access,
                  UUID createdByUserId, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.name = requireName(name);
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.keyHash = Objects.requireNonNull(keyHash, "keyHash");
        this.access = Objects.requireNonNull(access, "access");
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
    }

    private static String requireName(String name) {
        var trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Give the key a name, such as the system that will use it");
        if (trimmed.length() > 80) throw new IllegalArgumentException("Key names can be up to 80 characters");
        return trimmed;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(OffsetDateTime now) {
        if (revokedAt == null) revokedAt = now;
    }

    /** Returns true when the timestamp was moved, so the caller knows to save. */
    public boolean markUsed(OffsetDateTime now) {
        if (lastUsedAt != null && lastUsedAt.plus(LAST_USED_PRECISION).isAfter(now)) return false;
        lastUsedAt = now;
        return true;
    }

    public UUID id() { return id; }
    public UUID workspaceId() { return workspaceId; }
    public String name() { return name; }
    public String prefix() { return prefix; }
    public ApiKeyAccess access() { return access; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime lastUsedAt() { return lastUsedAt; }
    public OffsetDateTime revokedAt() { return revokedAt; }
}
