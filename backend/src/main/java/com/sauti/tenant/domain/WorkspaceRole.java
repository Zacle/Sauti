package com.sauti.tenant.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** A member's role in one workspace. The same person can hold different roles in different workspaces. */
public enum WorkspaceRole {
    OWNER(EnumSet.allOf(Permission.class)),
    ADMIN(EnumSet.of(Permission.VIEW, Permission.HANDLE_CONVERSATIONS, Permission.CONFIGURE, Permission.MANAGE_MEMBERS)),
    MEMBER(EnumSet.of(Permission.VIEW, Permission.HANDLE_CONVERSATIONS));

    private final Set<Permission> permissions;

    WorkspaceRole(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    /** Parses an API value such as "admin"; rejects unknown roles instead of defaulting. */
    public static WorkspaceRole parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Role is required");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Role must be owner, admin or member");
        }
    }
}
