package com.sauti.auth;

import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class User extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role = "OWNER";

    @Column(nullable = false)
    private boolean emailVerified = false;

    /** Console appearance chosen by this person; follows them across browsers. */
    @Column(nullable = false, length = 16)
    private String consoleThemeMode = "light";

    @Column(nullable = false, length = 16)
    private String consoleThemeAccent = "green";

    public static final Set<String> CONSOLE_THEME_MODES = Set.of("light", "dark", "system");
    public static final Set<String> CONSOLE_THEME_ACCENTS = Set.of("green", "blue", "violet", "amber", "rose", "graphite");

    protected User() {
    }

    public User(Tenant tenant, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getConsoleThemeMode() {
        return consoleThemeMode;
    }

    public String getConsoleThemeAccent() {
        return consoleThemeAccent;
    }

    public void configureConsoleAppearance(String mode, String accent) {
        var normalizedMode = mode == null ? "" : mode.trim().toLowerCase();
        var normalizedAccent = accent == null ? "" : accent.trim().toLowerCase();
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
