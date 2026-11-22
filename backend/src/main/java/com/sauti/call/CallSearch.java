package com.sauti.call;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * What the owner is looking for in their calls. Text matches the transcript, the summary, the
 * caller's number, the intent and the agent's name; the caller can also pass calls found through
 * their bookings ({@code alsoMatching}).
 */
public record CallSearch(
        String text,
        OffsetDateTime from,
        OffsetDateTime to,
        String type,
        String status,
        UUID agentId,
        Collection<UUID> alsoMatching
) {
    /** Calls that reached a conclusion; the dashboard calls these "answered". */
    public static final Set<String> ANSWERED = Set.of("completed", "booking", "booked", "faq_answered", "transferred");
    public static final int MAX_TEXT = 100;

    public CallSearch {
        text = text == null ? "" : text.trim();
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        type = type == null || type.isBlank() ? "all" : type;
        status = status == null || status.isBlank() ? "all" : status;
        alsoMatching = alsoMatching == null ? List.of() : List.copyOf(alsoMatching);
    }

    /** The text as a lower-case {@code like} pattern, with {@code !} escaping the wildcards. */
    public String pattern() {
        if (text.isEmpty()) return null;
        return "%" + text.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /** The longest excerpt shown under a search result. */
    public static final int EXCERPT = 140;

    /**
     * Where the text was found, to show under the result: the transcript line that contains it
     * (with who said it), else the summary, cut to about {@value #EXCERPT} characters around the
     * match. Null when there is no text, or it matched elsewhere (number, intent, agent, booking).
     */
    public String excerpt(String transcript, String summary) {
        if (text.isEmpty()) return null;
        var needle = text.toLowerCase(Locale.ROOT);
        if (transcript != null) {
            for (var line : transcript.split("\n")) {
                var at = line.toLowerCase(Locale.ROOT).indexOf(needle);
                if (at >= 0) return around(line.trim(), at - (line.length() - line.stripLeading().length()), needle.length());
            }
        }
        if (summary != null) {
            var at = summary.toLowerCase(Locale.ROOT).indexOf(needle);
            if (at >= 0) return around(summary.trim(), at - (summary.length() - summary.stripLeading().length()), needle.length());
        }
        return null;
    }

    private static String around(String line, int at, int length) {
        if (line.length() <= EXCERPT) return line;
        // Keep a speaker prefix such as "Caller: " when the match is far into a long line.
        var speaker = line.startsWith("Caller: ") ? "Caller: " : line.startsWith("Agent: ") ? "Agent: " : "";
        var room = EXCERPT - speaker.length() - 2;
        var start = Math.max(speaker.length(), Math.min(at - room / 3, line.length() - room));
        var end = Math.min(line.length(), start + room);
        var excerpt = line.substring(start, end).trim();
        return speaker + (start > speaker.length() ? "…" : "") + excerpt + (end < line.length() ? "…" : "");
    }

    Specification<Call> forTenant(UUID tenantId) {
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            where.add(cb.equal(root.get("tenant").get("id"), tenantId));
            if (from != null) where.add(cb.greaterThanOrEqualTo(root.get("startedAt"), from));
            if (to != null) where.add(cb.lessThan(root.get("startedAt"), to));
            switch (type) {
                case "phone" -> where.add(root.get("direction").in("inbound", "outbound"));
                case "test" -> where.add(cb.equal(root.get("direction"), "test"));
                default -> where.add(cb.notEqual(root.get("direction"), "whatsapp"));
            }
            switch (status) {
                case "answered" -> where.add(root.get("outcome").in(ANSWERED));
                case "missed" -> where.add(cb.not(root.get("outcome").in(union(ANSWERED, "active"))));
                case "active" -> where.add(cb.equal(root.get("outcome"), "active"));
                default -> { }
            }
            if (agentId != null) where.add(cb.equal(root.get("agent").get("id"), agentId));
            var pattern = pattern();
            if (pattern != null) where.add(matchesText(root, cb, pattern));
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private Predicate matchesText(Root<Call> root, CriteriaBuilder cb, String pattern) {
        var matches = new ArrayList<Predicate>();
        for (var field : List.of("transcript", "callSummary", "callerNumber", "intent")) {
            matches.add(cb.like(cb.lower(root.get(field)), pattern, '!'));
        }
        matches.add(cb.like(cb.lower(root.get("agent").get("name")), pattern, '!'));
        // Intents are stored snake_case: "book cleaning" finds book_cleaning.
        if (pattern.contains(" ")) {
            matches.add(cb.like(cb.lower(root.get("intent")), pattern.replace(" ", "!_"), '!'));
        }
        if (!alsoMatching.isEmpty()) matches.add(root.get("id").in(alsoMatching));
        return cb.or(matches.toArray(Predicate[]::new));
    }

    private static Set<String> union(Set<String> values, String extra) {
        var all = new java.util.HashSet<>(values);
        all.add(extra);
        return all;
    }
}
