package com.sauti.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {{placeholder}} language used in agent prompts and greetings.
 *
 * <p>Placeholders are filled at call time, never at save time. A sentence whose placeholders all
 * resolve to nothing is left out, so an unset optional detail never reaches a caller as
 * "Our website is ." A sentence where only some placeholders are empty keeps its filled values.
 */
public final class PromptTemplate {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][a-z0-9_]{0,99})}}");
    private static final Pattern ANY_BRACES = Pattern.compile("\\{\\{([^{}]*)}}");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern DOUBLE_SPACE = Pattern.compile("(?<=\\S)[ \\t]{2,}");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("[ \\t]+([.,;:!?)])");

    private PromptTemplate() {
    }

    /** Every well-formed placeholder key, in order of first appearance. */
    public static Set<String> placeholders(String... texts) {
        var keys = new LinkedHashSet<String>();
        for (var text : texts) {
            if (text == null) continue;
            var matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) keys.add(matcher.group(1));
        }
        return keys;
    }

    /** Brace expressions that look like placeholders but would never be filled, e.g. {{Business Name}}. */
    public static List<String> malformed(String... texts) {
        var invalid = new ArrayList<String>();
        for (var text : texts) {
            if (text == null) continue;
            var matcher = ANY_BRACES.matcher(text);
            while (matcher.find()) {
                if (!PLACEHOLDER.matcher(matcher.group()).matches() && !invalid.contains(matcher.group())) {
                    invalid.add(matcher.group());
                }
            }
        }
        return invalid;
    }

    /** Fills placeholders from {@code values}; a missing or blank value counts as empty. */
    public static String render(String text, Function<String, String> values) {
        if (text == null || text.isBlank()) return "";
        var lines = text.split("\\r?\\n", -1);
        var rendered = new ArrayList<String>(lines.length);
        for (var line : lines) {
            if (!ANY_BRACES.matcher(line).find()) {
                rendered.add(line);
                continue;
            }
            var kept = new ArrayList<String>();
            for (var sentence : SENTENCE_END.split(line)) {
                var filled = fill(sentence, values);
                if (filled != null) kept.add(filled);
            }
            if (!kept.isEmpty()) rendered.add(String.join(" ", kept));
        }
        return String.join("\n", rendered);
    }

    /** Returns the filled sentence, or null when it had placeholders and every one of them is empty. */
    private static String fill(String sentence, Function<String, String> values) {
        // Any brace expression is consumed, so a malformed legacy placeholder never reaches a caller.
        var matcher = ANY_BRACES.matcher(sentence);
        var result = new StringBuilder();
        boolean anyPlaceholder = false;
        boolean anyFilled = false;
        while (matcher.find()) {
            anyPlaceholder = true;
            var key = matcher.group(1).trim();
            var value = PLACEHOLDER.matcher("{{" + key + "}}").matches() ? values.apply(key) : null;
            var text = value == null ? "" : value.trim();
            if (!text.isEmpty()) anyFilled = true;
            matcher.appendReplacement(result, Matcher.quoteReplacement(text));
        }
        matcher.appendTail(result);
        if (anyPlaceholder && !anyFilled) return null;
        if (!anyPlaceholder) return sentence;
        var tidied = SPACE_BEFORE_PUNCTUATION.matcher(result.toString()).replaceAll("$1");
        return DOUBLE_SPACE.matcher(tidied).replaceAll(" ");
    }
}
