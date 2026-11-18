package com.sauti.tenant.application;

import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.domain.ApiKey;
import com.sauti.tenant.domain.ApiKeyAccess;
import com.sauti.tenant.domain.ApiKeyRepository;
import com.sauti.tenant.domain.MembershipPolicy;
import com.sauti.tenant.domain.MembershipRepository;
import com.sauti.tenant.domain.MembershipRuleException;
import com.sauti.tenant.domain.Permission;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import com.sauti.tenant.domain.WorkspaceStatus;
import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace API keys. The full key is shown once, at creation; Sauti stores only its SHA-256 hash.
 * Keys look like {@code sauti_sk_<prefix>_<secret>} so they are easy to spot in code and logs.
 */
@Service
public class ApiKeyService {
    public static final String KEY_START = "sauti_sk_";
    static final int MAX_ACTIVE_KEYS = 20;

    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final ApiKeyRepository keys;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public ApiKeyService(TenantRepository tenants, MembershipRepository memberships, ApiKeyRepository keys,
                         ApplicationEventPublisher events) {
        this(tenants, memberships, keys, events, Clock.systemUTC());
    }

    ApiKeyService(TenantRepository tenants, MembershipRepository memberships, ApiKeyRepository keys,
                  ApplicationEventPublisher events, Clock clock) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.keys = keys;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CreatedApiKey create(UUID workspaceId, UUID actorUserId, String name, ApiKeyAccess access) {
        tenants.findLockedById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
        var actor = memberships.find(workspaceId, actorUserId).orElseThrow(WorkspaceNotFoundException::new);
        MembershipPolicy.requirePermission(actor, Permission.MANAGE_MEMBERS);
        if (keys.countByWorkspaceIdAndRevokedAtIsNull(workspaceId) >= MAX_ACTIVE_KEYS) {
            throw new MembershipRuleException("This workspace already has " + MAX_ACTIVE_KEYS + " active keys. Revoke one first.");
        }
        var prefix = randomHex(4);
        var secret = KEY_START + prefix + "_" + randomSecret();
        var key = keys.save(new ApiKey(workspaceId, name, prefix, hash(secret), access, actorUserId, now()));
        events.publishEvent(WorkspaceActivity.byPerson(workspaceId, actorUserId, "api_key.created", "api_key",
                key.id().toString(), "Created API key \"" + key.name() + "\" with " + describe(access)));
        return new CreatedApiKey(ApiKeyView.of(key), secret);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyView> keys(UUID workspaceId) {
        return keys.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream().map(ApiKeyView::of).toList();
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID actorUserId, UUID keyId) {
        var actor = memberships.find(workspaceId, actorUserId).orElseThrow(WorkspaceNotFoundException::new);
        MembershipPolicy.requirePermission(actor, Permission.MANAGE_MEMBERS);
        var key = keys.findByIdAndWorkspaceId(keyId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("API key not found"));
        if (!key.isActive()) return;
        key.revoke(now());
        keys.save(key);
        events.publishEvent(WorkspaceActivity.byPerson(workspaceId, actorUserId, "api_key.revoked", "api_key",
                key.id().toString(), "Revoked API key \"" + key.name() + "\""));
    }

    /**
     * Checks a presented key. Unknown, revoked, and closed-workspace keys all fail the same way.
     * Suspended workspaces are reported so the caller can answer 423.
     */
    @Transactional
    public Optional<ApiKeyIdentity> authenticate(String presented) {
        if (presented == null || !presented.startsWith(KEY_START)) return Optional.empty();
        var key = keys.findByKeyHash(hash(presented.trim())).filter(ApiKey::isActive);
        if (key.isEmpty()) return Optional.empty();
        var status = tenants.findById(key.get().workspaceId()).map(t -> t.workspaceStatus()).orElse(WorkspaceStatus.CLOSED);
        if (status == WorkspaceStatus.CLOSED) return Optional.empty();
        if (key.get().markUsed(now())) keys.save(key.get());
        return Optional.of(new ApiKeyIdentity(key.get().id(), key.get().workspaceId(), key.get().name(),
                key.get().access().permissions(), status));
    }

    private static String describe(ApiKeyAccess access) {
        return access == ApiKeyAccess.FULL ? "full access" : "read-only access";
    }

    private String randomHex(int bytes) {
        var buffer = new byte[bytes];
        random.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    private String randomSecret() {
        var buffer = new byte[32];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public record ApiKeyView(UUID id, String name, String hint, ApiKeyAccess access, OffsetDateTime createdAt,
                             OffsetDateTime lastUsedAt, OffsetDateTime revokedAt) {
        static ApiKeyView of(ApiKey key) {
            return new ApiKeyView(key.id(), key.name(), KEY_START + key.prefix() + "_…", key.access(), key.createdAt(),
                    key.lastUsedAt(), key.revokedAt());
        }
    }

    /** Returned once. {@code secret} is never stored and cannot be shown again. */
    public record CreatedApiKey(ApiKeyView key, String secret) {
    }

    public record ApiKeyIdentity(UUID keyId, UUID workspaceId, String name, Set<Permission> permissions,
                                 WorkspaceStatus workspaceStatus) {
    }
}
