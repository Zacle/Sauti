package com.sauti.auth.application;

import com.sauti.auth.UnverifiedEmailException;
import com.sauti.auth.application.port.PasswordHasher;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email and password sign-in. An unknown email, an account without a password and a wrong
 * password give the same error after the same amount of work.
 */
@Service
public class PasswordSignIn {
    static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordHasher passwords;

    public PasswordSignIn(UserRepository users, PasswordHasher passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public User signIn(String email, String password) {
        var user = users.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null || !user.hasPassword()) {
            passwords.spendEquivalentTime(password);
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException(INVALID_CREDENTIALS);
        }
        if (!user.isEmailVerified()) {
            throw new UnverifiedEmailException("Verify your email before logging in");
        }
        return user;
    }
}
