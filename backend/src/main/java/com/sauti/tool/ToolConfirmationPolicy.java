package com.sauti.tool;

import java.util.Arrays;

/** How caller authorization must be established before a side effect executes. */
public enum ToolConfirmationPolicy {
    NONE("none"),
    EXPLICIT("explicit"),
    VERIFIED_REVIEW("verified_review");

    private final String value;

    ToolConfirmationPolicy(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ToolConfirmationPolicy from(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equalsIgnoreCase(clean(value)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported tool confirmation policy: " + value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
