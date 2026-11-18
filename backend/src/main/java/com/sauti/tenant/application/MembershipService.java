package com.sauti.tenant.application;

import com.sauti.tenant.TenantRepository;
import com.sauti.tenant.application.WorkspaceViews.MemberView;
import com.sauti.tenant.application.port.MemberDirectory;
import com.sauti.tenant.domain.Membership;
import com.sauti.tenant.domain.MembershipPolicy;
import com.sauti.tenant.domain.MembershipRepository;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import com.sauti.tenant.domain.WorkspaceRole;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is in a workspace and with which role. Every change locks the workspace row first so two
 * concurrent requests cannot both pass the "at least one Owner" check.
 */
@Service
public class MembershipService {
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final MemberDirectory directory;
    private final WorkspaceAccessService access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public MembershipService(TenantRepository tenants, MembershipRepository memberships,
                             MemberDirectory directory, WorkspaceAccessService access, ApplicationEventPublisher events) {
        this(tenants, memberships, directory, access, events, Clock.systemUTC());
    }

    MembershipService(TenantRepository tenants, MembershipRepository memberships, MemberDirectory directory,
                      WorkspaceAccessService access, ApplicationEventPublisher events, Clock clock) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.directory = directory;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID workspaceId) {
        var all = memberships.findAllByIdTenantIdOrderByCreatedAtAsc(workspaceId);
        var profiles = directory.profiles(all.stream().map(Membership::userId).toList());
        return all.stream()
                .map(membership -> MemberView.of(membership, profiles.containsKey(membership.userId())
                        ? profiles.get(membership.userId()).email() : ""))
                .toList();
    }

    @Transactional
    public MemberView changeRole(UUID workspaceId, UUID actorUserId, UUID targetUserId, WorkspaceRole newRole) {
        lockWorkspace(workspaceId);
        var actor = actor(workspaceId, actorUserId);
        var target = target(workspaceId, targetUserId);
        var previous = target.role();
        MembershipPolicy.changeRole(actor, target, newRole, memberships.ownerCount(workspaceId), now());
        memberships.save(target);
        access.evict(workspaceId, targetUserId);
        if (previous != newRole) {
            record(workspaceId, actorUserId, "member.role_changed", targetUserId,
                    "Changed " + emailOf(targetUserId) + " from " + label(previous) + " to " + label(newRole));
        }
        return MemberView.of(target, emailOf(targetUserId));
    }

    @Transactional
    public void remove(UUID workspaceId, UUID actorUserId, UUID targetUserId) {
        lockWorkspace(workspaceId);
        var actor = actor(workspaceId, actorUserId);
        var target = target(workspaceId, targetUserId);
        MembershipPolicy.requireCanRemove(actor, target, memberships.ownerCount(workspaceId));
        memberships.delete(target);
        access.evict(workspaceId, targetUserId);
        if (actorUserId.equals(targetUserId)) {
            record(workspaceId, actorUserId, "member.left", targetUserId, "Left the workspace");
        } else {
            record(workspaceId, actorUserId, "member.removed", targetUserId, "Removed " + emailOf(targetUserId));
        }
    }

    @Transactional
    public void transferOwnership(UUID workspaceId, UUID actorUserId, UUID newOwnerUserId) {
        lockWorkspace(workspaceId);
        var actor = actor(workspaceId, actorUserId);
        var target = target(workspaceId, newOwnerUserId);
        MembershipPolicy.transferOwnership(actor, target, now());
        memberships.save(target);
        memberships.save(actor);
        access.evict(workspaceId, actorUserId);
        access.evict(workspaceId, newOwnerUserId);
        record(workspaceId, actorUserId, "ownership.transferred", newOwnerUserId,
                "Made " + emailOf(newOwnerUserId) + " the Owner");
    }

    /**
     * Open workspaces where this person is the only Owner. If they delete their account these are
     * closed with it, so the list says how many other people would lose access.
     */
    @Transactional(readOnly = true)
    public List<SoleOwnedWorkspace> soleOwnedWorkspaces(UUID userId) {
        return memberships.findAllByIdUserId(userId).stream()
                .filter(Membership::isOwner)
                .filter(membership -> memberships.ownerCount(membership.workspaceId()) <= 1)
                .flatMap(membership -> tenants.findById(membership.workspaceId())
                        .filter(tenant -> tenant.workspaceStatus() != com.sauti.tenant.domain.WorkspaceStatus.CLOSED)
                        .map(tenant -> new SoleOwnedWorkspace(tenant.getId(), tenant.getBusinessName(),
                                memberships.findAllByIdTenantIdOrderByCreatedAtAsc(tenant.getId()).size() - 1))
                        .stream())
                .toList();
    }

    public record SoleOwnedWorkspace(UUID workspaceId, String name, int otherMembers) {
    }

    /** Removes the person from every workspace, as if they left each one. Fails if any would lose its last Owner. */
    @Transactional
    public void leaveAll(UUID userId) {
        for (var membership : memberships.findAllByIdUserId(userId)) {
            var closed = tenants.findById(membership.workspaceId())
                    .map(tenant -> tenant.workspaceStatus() == com.sauti.tenant.domain.WorkspaceStatus.CLOSED).orElse(true);
            if (closed) {
                memberships.delete(membership);
                access.evict(membership.workspaceId(), userId);
            } else {
                remove(membership.workspaceId(), userId, userId);
            }
        }
    }

    /** Adds a person with the given role; an existing member keeps their current role. */
    @Transactional
    public Membership join(UUID workspaceId, UUID userId, WorkspaceRole role) {
        var existing = memberships.find(workspaceId, userId);
        if (existing.isPresent()) return existing.get();
        var membership = memberships.save(new Membership(workspaceId, userId, role, now()));
        access.evict(workspaceId, userId);
        record(workspaceId, userId, "member.joined", userId, "Joined as " + label(role));
        return membership;
    }

    @Transactional(readOnly = true)
    public Membership requireMember(UUID workspaceId, UUID userId) {
        return actor(workspaceId, userId);
    }

    private void lockWorkspace(UUID workspaceId) {
        tenants.findLockedById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
    }

    private Membership actor(UUID workspaceId, UUID userId) {
        return memberships.find(workspaceId, userId).orElseThrow(WorkspaceNotFoundException::new);
    }

    private Membership target(UUID workspaceId, UUID userId) {
        return memberships.find(workspaceId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found"));
    }

    private String emailOf(UUID userId) {
        var profile = directory.profiles(List.of(userId)).get(userId);
        return profile == null ? "" : profile.email();
    }

    private void record(UUID workspaceId, UUID actorUserId, String action, UUID targetUserId, String summary) {
        events.publishEvent(WorkspaceActivity.byPerson(workspaceId, actorUserId, action, "member",
                targetUserId.toString(), summary));
    }

    static String label(WorkspaceRole role) {
        var lower = role.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
