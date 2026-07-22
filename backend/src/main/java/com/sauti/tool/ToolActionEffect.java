package com.sauti.tool;

import java.util.Arrays;

/** Platform-level effect of executing a tool, independent of its business name. */
public enum ToolActionEffect {
    READ_ONLY("read_only", false),
    DATA_WRITE("data_write", true),
    EXTERNAL_COMMUNICATION("external_communication", true),
    FINANCIAL("financial", true),
    TRANSFER("transfer", true),
    TERMINAL("terminal", true);

    private final String value;
    private final boolean sideEffecting;

    ToolActionEffect(String value, boolean sideEffecting) {
        this.value = value;
        this.sideEffecting = sideEffecting;
    }

    public String value() {
        return value;
    }

    public boolean isSideEffecting() {
        return sideEffecting;
    }

    public static ToolActionEffect from(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equalsIgnoreCase(clean(value)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported tool action effect: " + value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
