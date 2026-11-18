package com.sauti.auth.application;

import com.sauti.auth.InvalidSessionException;
import com.sauti.auth.application.AuthDtos.ConsoleAppearanceRequest;
import com.sauti.auth.application.AuthDtos.ConsoleAppearanceResponse;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.PasswordPolicy;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.application.WorkspaceService;
import com.sauti.tenant.domain.WorkspaceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settings that belong to the person rather than to any workspace. */
@Service
public class AccountSettings {
    private final UserRepository users;
    private final PasswordHasher passwords;
    private final SessionService sessions;
    private final WorkspaceService workspaces;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AccountSettings(UserRepository users, PasswordHasher passwords, SessionService sessions,
                           WorkspaceService workspaces, org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.workspaces = workspaces;
    }

    /**
     * Changes the password and ends every session, including this one; the caller starts a fresh
     * session for the current browser. An account created through Google may set its first password
     * without a current one.
     */
    @Transactional
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        var user = requireUser(userId);
        if (user.hasPassword()) {
            if (currentPassword == null || !passwords.matches(currentPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            if (passwords.matches(newPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("Choose a password different from your current password");
            }
        }
        PasswordPolicy.validate(newPassword, user.getEmail());
        user.changePasswordHash(passwords.hash(newPassword));
        users.save(user);
        events.publishEvent(new AccountActivity(userId, "password.changed", "Changed the password"));
        sessions.endAll(userId);
        return user;
    }

    @Transactional(readOnly = true)
    public ConsoleAppearanceResponse appearance(UUID userId) {
        return ConsoleAppearanceResponse.from(requireUser(userId));
    }

    @Transactional
    public ConsoleAppearanceResponse configureAppearance(UUID userId, ConsoleAppearanceRequest request) {
        var user = requireUser(userId);
        user.configureConsoleAppearance(request.mode(), request.accent());
        users.save(user);
        return ConsoleAppearanceResponse.from(user);
    }

    /** Remembers which workspace to reopen on the next sign-in, on any device. */
    @Transactional
    public void rememberWorkspace(UUID userId, UUID workspaceId) {
        if (!workspaces.isOpenMember(userId, workspaceId)) throw new WorkspaceNotFoundException();
        var user = requireUser(userId);
        user.rememberWorkspace(workspaceId);
        users.save(user);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(InvalidSessionException::new);
    }
}
