package com.sauti.tenant.domain;

import java.util.Locale;

/** Whether a workspace can be used. Checked on every workspace request, not only at sign-in. */
public enum WorkspaceStatus {
    ACTIVE,
    /** Temporarily blocked by Sauti, for example for abuse or non-payment. Data is kept. */
    SUSPENDED,
    /** Closed by its Owner. Treated as not found. */
    CLOSED;

    public static WorkspaceStatus fromStored(String value) {
        if (value == null) return ACTIVE;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "suspended" -> SUSPENDED;
            case "closed" -> CLOSED;
            default -> ACTIVE;
        };
    }

    public String stored() {
        return name().toLowerCase(Locale.ROOT);
    }
}
