package com.sauti.tenant.application;

import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.application.WorkspaceViews.WorkspaceAccess;
import com.sauti.tenant.domain.MembershipRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Answers "may this person act in this workspace, and as what?" for every workspace request.
 *
 * <p>Results are cached for {@link #TTL} per application instance. A change made on this instance
 * evicts immediately; on other instances it takes effect within the TTL. That bound is the
 * accepted delay for removing a member or suspending a workspace.
 */
@Service
public class WorkspaceAccessService {
    static final Duration TTL = Duration.ofSeconds(30);
    private static final int MAX_ENTRIES = 10_000;

    private final MembershipRepository memberships;
    private final TenantRepository tenants;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<Key, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public WorkspaceAccessService(MembershipRepository memberships, TenantRepository tenants) {
        this(memberships, tenants, System::nanoTime);
    }

    WorkspaceAccessService(MembershipRepository memberships, TenantRepository tenants, LongSupplier nanoClock) {
        this.memberships = memberships;
        this.tenants = tenants;
        this.nanoClock = nanoClock;
    }

    public Optional<WorkspaceAccess> resolve(UUID userId, UUID workspaceId) {
        var key = new Key(workspaceId, userId);
        var now = nanoClock.getAsLong();
        var cached = cache.get(key);
        if (cached != null && cached.expiresAtNanos - now > 0) return cached.access;
        var access = load(userId, workspaceId);
        if (cache.size() >= MAX_ENTRIES) cache.clear();
        cache.put(key, new Cached(access, now + TTL.toNanos()));
        return access;
    }

    public void evict(UUID workspaceId, UUID userId) {
        cache.remove(new Key(workspaceId, userId));
    }

    public void evictWorkspace(UUID workspaceId) {
        cache.keySet().removeIf(key -> key.workspaceId.equals(workspaceId));
    }

    private Optional<WorkspaceAccess> load(UUID userId, UUID workspaceId) {
        return memberships.find(workspaceId, userId)
                .flatMap(membership -> tenants.findById(workspaceId)
                        .map(tenant -> new WorkspaceAccess(workspaceId, membership.role(), tenant.workspaceStatus())));
    }

    private record Key(UUID workspaceId, UUID userId) {
    }

    private record Cached(Optional<WorkspaceAccess> access, long expiresAtNanos) {
    }
}
