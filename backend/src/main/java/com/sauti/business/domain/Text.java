package com.sauti.business.domain;

/** Small text rules shared by the business profile. */
final class Text {
    private Text() {
    }

    /** Trimmed text, or null when nothing is left. */
    static String clean(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String limit(String value, int max, String what) {
        if (value != null && value.length() > max) {
            throw new BusinessProfileException(what + " are at most " + max + " characters");
        }
        return value;
    }
}
