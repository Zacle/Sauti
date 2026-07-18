package com.sauti.agent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves a customer-facing business identity without falling back to the
 * workspace/account name. The workspace is an administrative boundary and is
 * not necessarily the company represented by an individual agent.
 */
public final class AgentBusinessIdentity {
    private static final List<Pattern> PROMPT_PATTERNS = List.of(
            Pattern.compile("(?im)^\\s*(?:business|business name|gym name|clinic name)\\s*:\\s*([^\\r\\n#]+)$"),
            Pattern.compile("(?im)^\\s*-\\s*name\\s*:\\s*([^\\r\\n#]+)$"),
            Pattern.compile("(?i)\\b(?:virtual assistant|voice agent|assistant|receptionist)\\s+for\\s+([^\\r\\n.!?]{2,80})")
    );

    private AgentBusinessIdentity() {
    }

    public static String fromPrompt(Agent agent) {
        if (agent == null || agent.getSystemPrompt() == null) return "";
        var prompt = agent.getSystemPrompt();
        for (var pattern : PROMPT_PATTERNS) {
            var matcher = pattern.matcher(prompt);
            if (matcher.find()) {
                var candidate = clean(matcher.group(1));
                if (!candidate.isBlank() && !looksLikePlaceholder(candidate)) return candidate;
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value
                .replaceAll("\\s+", " ")
                .replaceAll("[,;:]$", "")
                .trim();
    }

    private static boolean looksLikePlaceholder(String value) {
        var normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("not provided")
                || normalized.equals("n/a")
                || normalized.equals("none")
                || normalized.contains("{{");
    }
}
