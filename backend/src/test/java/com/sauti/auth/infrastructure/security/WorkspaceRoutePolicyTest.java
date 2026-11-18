package com.sauti.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sauti.tenant.domain.Permission;
import com.sauti.tenant.domain.WorkspaceRole;
import org.junit.jupiter.api.Test;

class WorkspaceRoutePolicyTest {
    private final WorkspaceRoutePolicy policy = new WorkspaceRoutePolicy();

    @Test
    void readingNeedsViewAndChangesDefaultToConfigure() {
        assertThat(policy.requiredPermission("GET", "/agents")).isEqualTo(Permission.VIEW);
        assertThat(policy.requiredPermission("POST", "/agents")).isEqualTo(Permission.CONFIGURE);
        assertThat(policy.requiredPermission("PUT", "/tenant/workspace-profile")).isEqualTo(Permission.CONFIGURE);
        assertThat(policy.requiredPermission("POST", "/some-route-added-later")).isEqualTo(Permission.CONFIGURE);
    }

    @Test
    void billingAndOwnershipAreOwnerOnly() {
        assertThat(policy.requiredPermission("POST", "/billing/checkout")).isEqualTo(Permission.MANAGE_BILLING);
        assertThat(policy.requiredPermission("DELETE", "")).isEqualTo(Permission.OWN_WORKSPACE);
        assertThat(policy.requiredPermission("POST", "/transfer-ownership")).isEqualTo(Permission.OWN_WORKSPACE);
        assertThat(WorkspaceRole.ADMIN.grants(Permission.MANAGE_BILLING)).isFalse();
        assertThat(WorkspaceRole.ADMIN.grants(Permission.OWN_WORKSPACE)).isFalse();
    }

    @Test
    void membersCanHandleConversationsAndNotificationsButNotConfigure() {
        assertThat(policy.requiredPermission("POST", "/whatsapp/conversations/1/messages")).isEqualTo(Permission.HANDLE_CONVERSATIONS);
        assertThat(policy.requiredPermission("PATCH", "/notifications/1/read")).isEqualTo(Permission.HANDLE_CONVERSATIONS);
        assertThat(policy.requiredPermission("POST", "/dashboard/socket-ticket")).isEqualTo(Permission.VIEW);
        assertThat(WorkspaceRole.MEMBER.grants(Permission.HANDLE_CONVERSATIONS)).isTrue();
        assertThat(WorkspaceRole.MEMBER.grants(Permission.CONFIGURE)).isFalse();
    }

    @Test
    void invitationsNeedMemberManagement() {
        assertThat(policy.requiredPermission("POST", "/invitations")).isEqualTo(Permission.MANAGE_MEMBERS);
        assertThat(WorkspaceRole.ADMIN.grants(Permission.MANAGE_MEMBERS)).isTrue();
        assertThat(WorkspaceRole.MEMBER.grants(Permission.MANAGE_MEMBERS)).isFalse();
    }

    @Test
    void featureRoutesNeedAWorkspaceButAccountRoutesDoNot() {
        assertThat(policy.requiresWorkspace("/api/v1/agents")).isTrue();
        assertThat(policy.requiresWorkspace("/api/v1/tenant/webhook")).isTrue();
        assertThat(policy.requiresWorkspace("/api/v1/auth/login")).isFalse();
        assertThat(policy.requiresWorkspace("/api/v1/me")).isFalse();
        assertThat(policy.requiresWorkspace("/api/v1/workspaces")).isFalse();
        assertThat(policy.requiresWorkspace("/api/v1/admin/overview")).isFalse();
        assertThat(policy.requiresWorkspace("/webhooks/telnyx")).isFalse();
        assertThat(policy.requiresWorkspace("/api/v1/authority")).isTrue();
    }
}
