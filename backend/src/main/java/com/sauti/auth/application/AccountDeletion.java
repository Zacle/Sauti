package com.sauti.auth.application;

import com.sauti.auth.InvalidSessionException;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.PlatformAdminRepository;
import com.sauti.auth.domain.UserIdentityRepository;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.application.MembershipService;
import com.sauti.tenant.application.MembershipService.SoleOwnedWorkspace;
import com.sauti.tenant.application.WorkspaceService;
import com.sauti.tenant.domain.MembershipRuleException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an account (right to erasure). Anyone can delete their own account at any time.
 *
 * <p>Workspaces where the person is the only Owner are theirs to end: those workspaces are closed
 * with the account, and their other members lose access. Because that affects other people, the
 * caller must confirm it explicitly after seeing {@link #impact}. Workspaces with another Owner
 * stay open; the person simply leaves them. The account itself is anonymised.
 */
@Service
public class AccountDeletion {
    /** Typed by people who have no password (Google accounts) to confirm. */
    public static final String CONFIRMATION_PHRASE = "delete my account";

    private final UserRepository users;
    private final PasswordHasher passwords;
    private final MembershipService memberships;
    private final WorkspaceService workspaces;
    private final SessionService sessions;
    private final UserIdentityRepository identities;
    private final PlatformAdminRepository admins;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public AccountDeletion(UserRepository users, PasswordHasher passwords, MembershipService memberships,
                           WorkspaceService workspaces, SessionService sessions, UserIdentityRepository identities,
                           PlatformAdminRepository admins, ApplicationEventPublisher events) {
        this(users, passwords, memberships, workspaces, sessions, identities, admins, events, Clock.systemUTC());
    }

    AccountDeletion(UserRepository users, PasswordHasher passwords, MembershipService memberships,
                    WorkspaceService workspaces, SessionService sessions, UserIdentityRepository identities,
                    PlatformAdminRepository admins, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.memberships = memberships;
        this.workspaces = workspaces;
        this.sessions = sessions;
        this.identities = identities;
        this.admins = admins;
        this.events = events;
        this.clock = clock;
    }

    /** The workspaces that would close if this account were deleted now. */
    @Transactional(readOnly = true)
    public List<SoleOwnedWorkspace> impact(UUID userId) {
        return memberships.soleOwnedWorkspaces(userId);
    }

    @Transactional
    public void delete(UUID userId, String password, String confirmation, boolean closeOwnedWorkspaces) {
        var user = users.findById(userId).filter(found -> !found.isDeleted()).orElseThrow(InvalidSessionException::new);
        if (user.hasPassword()) {
            if (password == null || !passwords.matches(password, user.getPasswordHash())) {
                throw new IllegalArgumentException("Your password is incorrect");
            }
        } else if (confirmation == null || !CONFIRMATION_PHRASE.equalsIgnoreCase(confirmation.trim())) {
            throw new IllegalArgumentException("Type \"" + CONFIRMATION_PHRASE + "\" to confirm");
        }
        var owned = memberships.soleOwnedWorkspaces(userId);
        if (!owned.isEmpty() && !closeOwnedWorkspaces) {
            throw new MembershipRuleException("Deleting your account also closes " + owned.size()
                    + (owned.size() == 1 ? " workspace you own" : " workspaces you own")
                    + ". Confirm that you want to close " + (owned.size() == 1 ? "it" : "them") + ".");
        }
        events.publishEvent(new AccountActivity(userId, "account.deleted", "Deleted the account"));
        owned.forEach(workspace -> workspaces.close(workspace.workspaceId(), userId));
        memberships.leaveAll(userId);
        sessions.endAll(userId);
        identities.deleteAllByUserId(userId);
        if (admins.existsById(userId)) admins.deleteById(userId);
        user.anonymize(OffsetDateTime.now(clock));
        users.save(user);
    }
}
