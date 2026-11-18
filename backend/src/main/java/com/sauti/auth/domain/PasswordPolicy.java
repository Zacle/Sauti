package com.sauti.auth.domain;

import java.util.Locale;
import java.util.Set;

/** The rules a new password must meet. Checked on sign-up, reset and change. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 200;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "passw0rd", "12345678", "123456789",
            "1234567890", "11111111", "00000000", "qwerty123", "qwertyuiop", "iloveyou", "admin123",
            "welcome1", "letmein1", "abc12345", "sauti123", "sautisauti", "azerty123"
    );

    private PasswordPolicy() {
    }

    public static void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Use at least " + MIN_LENGTH + " characters for your password");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Use at most " + MAX_LENGTH + " characters for your password");
        }
        var lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || password.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("This password is too easy to guess. Choose another one");
        }
        if (email != null && !email.isBlank()) {
            var normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            var localPart = normalizedEmail.split("@", 2)[0];
            if (lower.equals(normalizedEmail) || lower.equals(localPart)) {
                throw new IllegalArgumentException("Your password can't be your email address");
            }
        }
    }
}
