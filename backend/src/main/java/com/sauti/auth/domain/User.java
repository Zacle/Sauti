package com.sauti.auth.domain;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A person who can sign in. Workspace membership lives in the workspace module; an account only
 * remembers which workspace it used last so the console can reopen it.
 */
@Entity
@Table(name = "app_users")
public class User extends Auditable {
    public static final Set<String> CONSOLE_THEME_MODES = Set.of("light", "dark", "system");
    public static final int DISPLAY_NAME_MAX = 80;
    public static final Set<String> CONSOLE_THEME_ACCENTS = Set.of("green", "blue", "violet", "amber", "rose", "graphite");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Null for accounts that have only ever signed in with Google. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(name = "last_workspace_id")
    private UUID lastWorkspaceId;

    private java.time.OffsetDateTime deletedAt;

    @Column(nullable = false, length = 16)
    private String consoleThemeMode = "light";

    @Column(nullable = false, length = 16)
    private String consoleThemeAccent = "green";

    /** What colleagues see on this person's work, such as WhatsApp replies; null until they set it. */
    @Column(length = DISPLAY_NAME_MAX)
    private String displayName;

    protected User() {
    }

    private User(String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
    }

    public static User withPassword(String email, String passwordHash) {
        return new User(email, Objects.requireNonNull(passwordHash, "passwordHash"));
    }

    /** An account created by an identity provider that has already verified the email. */
    public static User verifiedWithoutPassword(String email) {
        var user = new User(email, null);
        user.emailVerified = true;
        return user;
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Deletes the person from Sauti: the email, password and preferences go; the row stays,
     * anonymised, so history that mentions it remains consistent.
     */
    public void anonymize(java.time.OffsetDateTime now) {
        this.email = "deleted-" + id + "@deleted.invalid";
        this.passwordHash = null;
        this.emailVerified = false;
        this.lastWorkspaceId = null;
        this.displayName = null;
        this.deletedAt = now;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Sets the name colleagues see; blank clears it, so the email is shown instead. */
    public void rename(String name) {
        var value = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (value.length() > DISPLAY_NAME_MAX) {
            throw new IllegalArgumentException("Keep your name to " + DISPLAY_NAME_MAX + " characters or fewer");
        }
        this.displayName = value.isEmpty() ? null : value;
    }

    public UUID getLastWorkspaceId() {
        return lastWorkspaceId;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    public void rememberWorkspace(UUID workspaceId) {
        this.lastWorkspaceId = workspaceId;
    }

    public String getConsoleThemeMode() {
        return consoleThemeMode;
    }

    public String getConsoleThemeAccent() {
        return consoleThemeAccent;
    }

    public void configureConsoleAppearance(String mode, String accent) {
        var normalizedMode = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        var normalizedAccent = accent == null ? "" : accent.trim().toLowerCase(Locale.ROOT);
        if (!CONSOLE_THEME_MODES.contains(normalizedMode)) {
            throw new IllegalArgumentException("Theme mode must be light, dark or system");
        }
        if (!CONSOLE_THEME_ACCENTS.contains(normalizedAccent)) {
            throw new IllegalArgumentException("Unknown accent colour");
        }
        this.consoleThemeMode = normalizedMode;
        this.consoleThemeAccent = normalizedAccent;
    }
}
