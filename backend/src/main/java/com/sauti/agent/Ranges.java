package com.sauti.agent;

import java.util.Arrays;
import java.util.List;

/** Small helpers shared by the agent's settings objects. */
final class Ranges {
    private Ranges() {
    }

    static int clamp(Integer value, int minimum, int maximum, int fallback) {
        return value == null ? fallback : Math.max(minimum, Math.min(maximum, value));
    }

    static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of()
                : Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }
}
