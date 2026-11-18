package com.sauti.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPolicyTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-18T12:00:00Z");
    private final UUID workspace = UUID.randomUUID();

    @Test
    void theLastOwnerCannotBeDemotedOrRemoved() {
        var owner = member(WorkspaceRole.OWNER);

        assertThatThrownBy(() -> MembershipPolicy.changeRole(owner, owner, WorkspaceRole.ADMIN, 1, NOW))
                .isInstanceOf(MembershipRuleException.class);
        assertThatThrownBy(() -> MembershipPolicy.requireCanRemove(owner, owner, 1))
                .isInstanceOf(MembershipRuleException.class);
    }

    @Test
    void anOwnerCanStepDownWhenAnotherOwnerRemains() {
        var owner = member(WorkspaceRole.OWNER);

        MembershipPolicy.changeRole(owner, owner, WorkspaceRole.ADMIN, 2, NOW);

        assertThat(owner.role()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    void adminsManageMembersButNeverTouchOwners() {
        var admin = member(WorkspaceRole.ADMIN);
        var member = member(WorkspaceRole.MEMBER);
        var owner = member(WorkspaceRole.OWNER);

        MembershipPolicy.changeRole(admin, member, WorkspaceRole.ADMIN, 1, NOW);
        assertThat(member.role()).isEqualTo(WorkspaceRole.ADMIN);

        assertThatThrownBy(() -> MembershipPolicy.changeRole(admin, member, WorkspaceRole.OWNER, 1, NOW))
                .isInstanceOf(WorkspaceActionForbiddenException.class);
        assertThatThrownBy(() -> MembershipPolicy.requireCanRemove(admin, owner, 2))
                .isInstanceOf(WorkspaceActionForbiddenException.class);
        assertThatThrownBy(() -> MembershipPolicy.requireCanInvite(admin, WorkspaceRole.OWNER))
                .isInstanceOf(WorkspaceActionForbiddenException.class);
    }

    @Test
    void membersCannotManageOthersButCanLeave() {
        var member = member(WorkspaceRole.MEMBER);
        var other = member(WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> MembershipPolicy.requireCanRemove(member, other, 1))
                .isInstanceOf(WorkspaceActionForbiddenException.class);
        assertThatThrownBy(() -> MembershipPolicy.requireCanInvite(member, WorkspaceRole.MEMBER))
                .isInstanceOf(WorkspaceActionForbiddenException.class);
        assertThatCode(() -> MembershipPolicy.requireCanRemove(member, member, 1)).doesNotThrowAnyException();
    }

    @Test
    void transferringOwnershipKeepsThePreviousOwnerAsAdmin() {
        var owner = member(WorkspaceRole.OWNER);
        var admin = member(WorkspaceRole.ADMIN);

        MembershipPolicy.transferOwnership(owner, admin, NOW);

        assertThat(admin.role()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(owner.role()).isEqualTo(WorkspaceRole.ADMIN);
        assertThatThrownBy(() -> MembershipPolicy.transferOwnership(owner, admin, NOW))
                .isInstanceOf(WorkspaceActionForbiddenException.class);
    }

    @Test
    void invitationsExpireAndCanOnlyBeUsedOnce() {
        var invitation = new WorkspaceInvitation(workspace, " New@Example.com ", WorkspaceRole.MEMBER, "hash", null, NOW);

        assertThat(invitation.email()).isEqualTo("new@example.com");
        assertThat(invitation.isFor("NEW@example.com")).isTrue();
        assertThat(invitation.isPendingAt(NOW.plus(WorkspaceInvitation.LIFETIME))).isFalse();

        invitation.accept(UUID.randomUUID(), NOW.plusDays(1));
        assertThatThrownBy(() -> invitation.accept(UUID.randomUUID(), NOW.plusDays(2)))
                .isInstanceOf(InvitationUnavailableException.class);
        assertThatThrownBy(() -> invitation.reissue("new-hash", NOW.plusDays(2)))
                .isInstanceOf(InvitationUnavailableException.class);
    }

    private Membership member(WorkspaceRole role) {
        return new Membership(workspace, UUID.randomUUID(), role, NOW);
    }
}
