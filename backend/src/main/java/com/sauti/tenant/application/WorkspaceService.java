package com.sauti.tenant.application;

import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.application.WorkspaceViews.WorkspaceMembershipView;
import com.sauti.tenant.domain.Membership;
import com.sauti.tenant.domain.MembershipPolicy;
import com.sauti.tenant.domain.MembershipRepository;
import com.sauti.tenant.domain.Permission;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import com.sauti.tenant.domain.WorkspaceRole;
import com.sauti.tenant.domain.WorkspaceStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating, listing and closing workspaces. */
@Service
public class WorkspaceService {
    private static final int MAX_NAME_LENGTH = 120;

    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final WorkspaceAccessService access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public WorkspaceService(TenantRepository tenants, MembershipRepository memberships, WorkspaceAccessService access,
                            ApplicationEventPublisher events) {
        this(tenants, memberships, access, events, Clock.systemUTC());
    }

    WorkspaceService(TenantRepository tenants, MembershipRepository memberships, WorkspaceAccessService access,
                     ApplicationEventPublisher events, Clock clock) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    /** Creates a workspace with the caller as its only Owner. */
    @Transactional
    public WorkspaceMembershipView create(UUID ownerUserId, String ownerEmail, String businessName, String countryCode) {
        var tenant = tenants.save(new Tenant(requireName(businessName), ownerEmail, requireCountry(countryCode)));
        memberships.save(new Membership(tenant.getId(), ownerUserId, WorkspaceRole.OWNER, OffsetDateTime.now(clock)));
        events.publishEvent(new WorkspaceCreated(tenant.getId(), ownerUserId));
        events.publishEvent(WorkspaceActivity.byPerson(tenant.getId(), ownerUserId, "workspace.created", "workspace",
                tenant.getId().toString(), "Created the workspace"));
        return WorkspaceMembershipView.of(tenant, WorkspaceRole.OWNER);
    }

    /** Every open workspace the person belongs to, by name. Closed workspaces are left out. */
    @Transactional(readOnly = true)
    public List<WorkspaceMembershipView> workspacesFor(UUID userId) {
        var mine = memberships.findAllByIdUserId(userId);
        var byId = tenants.findAllById(mine.stream().map(Membership::workspaceId).toList()).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        return mine.stream()
                .filter(membership -> byId.containsKey(membership.workspaceId()))
                .filter(membership -> byId.get(membership.workspaceId()).workspaceStatus() != WorkspaceStatus.CLOSED)
                .map(membership -> WorkspaceMembershipView.of(byId.get(membership.workspaceId()), membership.role()))
                .sorted(Comparator.comparing(view -> view.workspace().businessName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceMembershipView workspaceFor(UUID userId, UUID workspaceId) {
        var membership = memberships.find(workspaceId, userId).orElseThrow(WorkspaceNotFoundException::new);
        var tenant = tenants.findById(workspaceId)
                .filter(found -> found.workspaceStatus() != WorkspaceStatus.CLOSED)
                .orElseThrow(WorkspaceNotFoundException::new);
        return WorkspaceMembershipView.of(tenant, membership.role());
    }

    @Transactional(readOnly = true)
    public boolean isOpenMember(UUID userId, UUID workspaceId) {
        return memberships.find(workspaceId, userId).isPresent()
                && tenants.findById(workspaceId).map(t -> t.workspaceStatus() != WorkspaceStatus.CLOSED).orElse(false);
    }

    /** Only an Owner can close a workspace. Data is kept; erasure is a separate request. */
    @Transactional
    public void close(UUID workspaceId, UUID actorUserId) {
        var tenant = tenants.findLockedById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
        var actor = memberships.find(workspaceId, actorUserId).orElseThrow(WorkspaceNotFoundException::new);
        MembershipPolicy.requirePermission(actor, Permission.OWN_WORKSPACE);
        tenant.close();
        access.evictWorkspace(workspaceId);
        events.publishEvent(WorkspaceActivity.byPerson(workspaceId, actorUserId, "workspace.closed", "workspace",
                workspaceId.toString(), "Closed the workspace"));
        events.publishEvent(new WorkspaceClosed(workspaceId));
    }

    static String requireName(String businessName) {
        var name = businessName == null ? "" : businessName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Workspace name is required");
        if (name.length() > MAX_NAME_LENGTH) throw new IllegalArgumentException("Workspace name is too long");
        return name;
    }

    static String requireCountry(String countryCode) {
        var code = countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2}")) throw new IllegalArgumentException("Choose the country the workspace operates in");
        return code;
    }
}
