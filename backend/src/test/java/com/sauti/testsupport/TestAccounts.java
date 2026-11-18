package com.sauti.testsupport;

import com.sauti.auth.application.PlatformAdminPolicy;
import com.sauti.auth.application.port.AccessTokens;
import com.sauti.auth.application.port.AccessTokens.AccessClaims;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import java.math.BigDecimal;
import com.sauti.tenant.application.MembershipService;
import com.sauti.tenant.application.WorkspaceService;
import com.sauti.tenant.domain.WorkspaceRole;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates signed-in people and workspaces for API tests without going through email verification. */
@Component
public class TestAccounts {
    public static final String PASSWORD = "correct-horse-battery";

    private final UserRepository users;
    private final WorkspaceService workspaces;
    private final MembershipService memberships;
    private final AccessTokens accessTokens;
    private final PasswordHasher passwords;
    private final PlatformAdminPolicy platformAdmins;
    private final PilotProvisioningPolicyService providerPolicies;

    public TestAccounts(UserRepository users, WorkspaceService workspaces, MembershipService memberships,
                        AccessTokens accessTokens, PasswordHasher passwords, PlatformAdminPolicy platformAdmins,
                        PilotProvisioningPolicyService providerPolicies) {
        this.providerPolicies = providerPolicies;
        this.users = users;
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.accessTokens = accessTokens;
        this.passwords = passwords;
        this.platformAdmins = platformAdmins;
    }

    /** A verified person who owns a new workspace. */
    public Member owner(String businessName, String email, String countryCode) {
        var user = verifiedUser(email);
        var workspace = workspaces.create(user.getId(), user.getEmail(), businessName, countryCode);
        user.rememberWorkspace(workspace.workspace().id());
        users.save(user);
        return new Member(user.getId(), workspace.workspace().id(), user.getEmail(), tokenFor(user));
    }

    /** A verified person added to an existing workspace with the given role. */
    public Member member(UUID workspaceId, String email, WorkspaceRole role) {
        var user = verifiedUser(email);
        memberships.join(workspaceId, user.getId(), role);
        return new Member(user.getId(), workspaceId, user.getEmail(), tokenFor(user));
    }

    /** New workspaces start with paid providers blocked; tests that buy numbers or place calls approve them first. */
    public void approveProviderSpending(UUID workspaceId) {
        providerPolicies.configure(workspaceId, "approved", "USD", new BigDecimal("1000"), true, true, true, true,
                "Approved for tests", "test");
    }

    public User verifiedUser(String email) {
        var user = User.withPassword(email, passwords.hash(PASSWORD));
        user.verifyEmail();
        return users.save(user);
    }

    public String tokenFor(User user) {
        return accessTokens.issue(new AccessClaims(user.getId(), user.getEmail(),
                platformAdmins.allows(user.getEmail()), UUID.randomUUID())).value();
    }

    public record Member(UUID userId, UUID workspaceId, String email, String token) {
        /** A workspace feature route, for example {@code path("/agents")}. */
        public String path(String featurePath) {
            return "/api/v1/workspaces/" + workspaceId + featurePath;
        }

        public String bearer() {
            return "Bearer " + token;
        }
    }
}
