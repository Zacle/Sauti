package com.sauti.tenant.application;

import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantDtos.TenantResponse;
import com.sauti.tenant.domain.Membership;
import com.sauti.tenant.domain.Permission;
import com.sauti.tenant.domain.WorkspaceInvitation;
import com.sauti.tenant.domain.WorkspaceRole;
import com.sauti.tenant.domain.WorkspaceStatus;
import com.sauti.tenant.application.port.MemberDirectory;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/** Read models returned by the workspace use cases. */
public final class WorkspaceViews {
    private WorkspaceViews() {
    }

    /** A workspace as seen by one member: the workspace plus that member's role and permissions. */
    public record WorkspaceMembershipView(TenantResponse workspace, WorkspaceRole role, Set<Permission> permissions) {
        public static WorkspaceMembershipView of(Tenant tenant, WorkspaceRole role) {
            return new WorkspaceMembershipView(TenantResponse.from(tenant), role, role.permissions());
        }
    }

    /** What the request filter needs on every workspace request. Cached briefly. */
    public record WorkspaceAccess(UUID workspaceId, WorkspaceRole role, WorkspaceStatus status) {
    }

    /** @param displayName the name the member chose, or null when they haven't set one */
    public record MemberView(UUID userId, String email, String displayName, WorkspaceRole role,
                             OffsetDateTime joinedAt) {
        public static MemberView of(Membership membership, MemberDirectory.MemberProfile profile) {
            return new MemberView(membership.userId(), profile == null ? "" : profile.email(),
                    profile == null ? null : profile.displayName(), membership.role(), membership.joinedAt());
        }
    }

    public record InvitationView(
            UUID id,
            String email,
            WorkspaceRole role,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt,
            boolean expired
    ) {
        public static InvitationView of(WorkspaceInvitation invitation, OffsetDateTime now) {
            return new InvitationView(invitation.id(), invitation.email(), invitation.role(),
                    invitation.createdAt(), invitation.expiresAt(), !invitation.isPendingAt(now));
        }
    }

    /** Shown to whoever opens an invitation link, before they sign in or create an account. */
    public record InvitationPreview(
            String workspaceName,
            String email,
            WorkspaceRole role,
            OffsetDateTime expiresAt,
            boolean accountExists
    ) {
    }
}
