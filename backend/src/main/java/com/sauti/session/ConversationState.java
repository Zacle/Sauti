package com.sauti.session;

import java.util.LinkedHashMap;
import java.util.Map;

/** Language-independent, model-extracted state for one live conversation. */
public record ConversationState(
        Map<String, String> values,
        String bookingSubject,
        String bookingIntent,
        long revision
) {
    public static final String SUBJECT_UNKNOWN = "unknown";
    public static final String SUBJECT_SELF = "self";
    public static final String SUBJECT_OTHER = "other";
    public static final String INTENT_UNKNOWN = "unknown";
    public static final String INTENT_INFORMATION = "information_only";
    public static final String INTENT_ACTIVE = "active";
    public static final String INTENT_PAUSED = "paused";

    public ConversationState {
        values = values == null ? Map.of() : Map.copyOf(values);
        bookingSubject = normalizedChoice(bookingSubject, SUBJECT_UNKNOWN);
        bookingIntent = normalizedChoice(bookingIntent, INTENT_UNKNOWN);
        revision = Math.max(0, revision);
    }

    public static ConversationState empty() {
        return new ConversationState(Map.of(), SUBJECT_UNKNOWN, INTENT_UNKNOWN, 0);
    }

    public Map<String, String> asNotes() {
        var notes = new LinkedHashMap<>(values);
        notes.put("conversation_state_revision", Long.toString(revision));
        if (!SUBJECT_UNKNOWN.equals(bookingSubject)) notes.put("booking_subject", bookingSubject);
        if (!INTENT_UNKNOWN.equals(bookingIntent)) notes.put("booking_intent", bookingIntent);
        return Map.copyOf(notes);
    }

    public String promptBlock() {
        var result = new StringBuilder(
                "AUTHORITATIVE SEMANTIC CALL STATE (language-independent; latest explicit caller correction wins):\n"
        );
        result.append("- revision: ").append(revision).append('\n');
        result.append("- booking_subject: ").append(bookingSubject).append('\n');
        result.append("- booking_intent: ").append(bookingIntent).append('\n');
        if (values.isEmpty()) {
            result.append("- collected values: none");
        } else {
            values.forEach((key, value) -> result.append("- ").append(key).append(": ").append(value).append('\n'));
            result.setLength(result.length() - 1);
        }
        return result.toString();
    }

    private static String normalizedChoice(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
