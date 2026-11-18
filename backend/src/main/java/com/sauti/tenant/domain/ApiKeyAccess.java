package com.sauti.tenant.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a workspace API key may do. Keys never get member, billing or ownership permissions:
 * those always need a person.
 */
public enum ApiKeyAccess {
    READ_ONLY(EnumSet.of(Permission.VIEW)),
    FULL(EnumSet.of(Permission.VIEW, Permission.HANDLE_CONVERSATIONS, Permission.CONFIGURE));

    private final Set<Permission> permissions;

    ApiKeyAccess(Set<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public static ApiKeyAccess parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Choose read-only or full access");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Access must be read_only or full");
        }
    }
}
