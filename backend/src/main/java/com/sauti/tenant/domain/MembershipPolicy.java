package com.sauti.tenant.domain;

import java.time.OffsetDateTime;

/**
 * The rules for changing who is in a workspace. Pure domain logic with no persistence, so every
 * rule is unit-tested directly. The invariant everything protects: a workspace always has an Owner.
 */
public final class MembershipPolicy {
    private MembershipPolicy() {
    }

    public static void requireCanInvite(Membership actor, WorkspaceRole invitedRole) {
        requirePermission(actor, Permission.MANAGE_MEMBERS);
        if (invitedRole == WorkspaceRole.OWNER && !actor.isOwner()) {
            throw new WorkspaceActionForbiddenException("Only an Owner can invite another Owner");
        }
    }

    public static void changeRole(Membership actor, Membership target, WorkspaceRole newRole,
                                  long ownerCount, OffsetDateTime now) {
        requirePermission(actor, Permission.MANAGE_MEMBERS);
        if (target.role() == newRole) return;
        if ((newRole == WorkspaceRole.OWNER || target.isOwner()) && !actor.isOwner()) {
            throw new WorkspaceActionForbiddenException("Only an Owner can grant or remove the Owner role");
        }
        if (target.isOwner() && ownerCount <= 1) {
            throw new MembershipRuleException("A workspace needs at least one Owner. Transfer ownership first.");
        }
        target.assignRole(newRole, now);
    }

    /** Removing yourself is leaving; anyone may leave except the last Owner. */
    public static void requireCanRemove(Membership actor, Membership target, long ownerCount) {
        boolean leaving = actor.userId().equals(target.userId());
        if (!leaving) {
            requirePermission(actor, Permission.MANAGE_MEMBERS);
            if (target.isOwner() && !actor.isOwner()) {
                throw new WorkspaceActionForbiddenException("Only an Owner can remove another Owner");
            }
        }
        if (target.isOwner() && ownerCount <= 1) {
            throw new MembershipRuleException("A workspace needs at least one Owner. Transfer ownership first.");
        }
    }

    /** The new Owner takes over; the previous Owner stays on as Admin. */
    public static void transferOwnership(Membership actor, Membership target, OffsetDateTime now) {
        requirePermission(actor, Permission.OWN_WORKSPACE);
        if (actor.userId().equals(target.userId())) {
            throw new MembershipRuleException("You already own this workspace");
        }
        target.assignRole(WorkspaceRole.OWNER, now);
        actor.assignRole(WorkspaceRole.ADMIN, now);
    }

    public static void requirePermission(Membership actor, Permission permission) {
        if (!actor.role().grants(permission)) {
            throw new WorkspaceActionForbiddenException("Your role does not allow this");
        }
    }
}
