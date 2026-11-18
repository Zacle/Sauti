package com.sauti.auth.application;

import com.sauti.auth.InvalidSessionException;
import com.sauti.auth.application.AuthDtos.AccountResponse;
import com.sauti.auth.application.AuthDtos.SessionResponse;
import com.sauti.auth.application.SessionService.SessionGrant;
import com.sauti.auth.domain.PlatformAdminRepository;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.application.WorkspaceService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds what the console needs to know about the signed-in person. */
@Service
public class AccountQueries {
    private final UserRepository users;
    private final WorkspaceService workspaces;
    private final PlatformAdminPolicy bootstrapAdmins;
    private final PlatformAdminRepository platformAdmins;

    public AccountQueries(UserRepository users, WorkspaceService workspaces, PlatformAdminPolicy bootstrapAdmins,
                          PlatformAdminRepository platformAdmins) {
        this.users = users;
        this.workspaces = workspaces;
        this.bootstrapAdmins = bootstrapAdmins;
        this.platformAdmins = platformAdmins;
    }

    @Transactional(readOnly = true)
    public AccountResponse account(UUID userId) {
        return account(users.findById(userId).filter(user -> !user.isDeleted()).orElseThrow(InvalidSessionException::new));
    }

    /**
     * Includes every open workspace. The last-used workspace is reported only if the person can
     * still open it; otherwise the first one is suggested, or none.
     */
    @Transactional(readOnly = true)
    public AccountResponse account(User user) {
        var memberships = workspaces.workspacesFor(user.getId());
        var remembered = user.getLastWorkspaceId();
        var stillOpen = remembered != null
                && memberships.stream().anyMatch(view -> view.workspace().id().equals(remembered));
        var preferred = stillOpen ? remembered : memberships.isEmpty() ? null : memberships.get(0).workspace().id();
        return new AccountResponse(user.getId(), user.getEmail(), user.isEmailVerified(), user.hasPassword(),
                isPlatformAdmin(user), preferred, memberships);
    }

    @Transactional(readOnly = true)
    public SessionResponse session(SessionGrant grant) {
        return new SessionResponse(grant.accessToken().value(), grant.accessToken().expiresInSeconds(), account(grant.user()),
                grant.platformAdminSession());
    }

    /** Can this person use Sauti administration (after the authenticator step)? */
    public boolean isPlatformAdmin(User user) {
        return platformAdmins.existsById(user.getId()) || bootstrapAdmins.allows(user.getEmail());
    }

    /** The name used in account emails: the person's first workspace, or their email. */
    @Transactional(readOnly = true)
    public String displayName(User user) {
        return workspaces.workspacesFor(user.getId()).stream()
                .findFirst()
                .map(view -> view.workspace().businessName())
                .orElse(user.getEmail());
    }
}
