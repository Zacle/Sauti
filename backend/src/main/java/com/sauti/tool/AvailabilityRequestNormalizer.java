package com.sauti.tool;

import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Converts natural Realtime tool arguments into the strict calendar contract. */
public final class AvailabilityRequestNormalizer {
    private static final Pattern CLOCK = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?|h|heures?)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("monday", DayOfWeek.MONDAY), Map.entry("lundi", DayOfWeek.MONDAY),
            Map.entry("tuesday", DayOfWeek.TUESDAY), Map.entry("mardi", DayOfWeek.TUESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY), Map.entry("mercredi", DayOfWeek.WEDNESDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY), Map.entry("jeudi", DayOfWeek.THURSDAY),
            Map.entry("friday", DayOfWeek.FRIDAY), Map.entry("vendredi", DayOfWeek.FRIDAY),
            Map.entry("saturday", DayOfWeek.SATURDAY), Map.entry("samedi", DayOfWeek.SATURDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY), Map.entry("dimanche", DayOfWeek.SUNDAY)
    );

    private AvailabilityRequestNormalizer() {
    }

    public static LlmToolCall normalize(Call call, LlmToolCall toolCall, String callerTranscript) {
        if (!"check_availability".equals(toolCall.name())) return toolCall;
        var arguments = new LinkedHashMap<String, Object>(toolCall.arguments());
        arguments.entrySet().removeIf(entry -> entry.getValue() == null);
        var rawDate = string(arguments.get("date"));
        var rawTime = string(arguments.get("time_preference"));
        var source = String.join(" ", rawDate, rawTime, callerTranscript == null ? "" : callerTranscript).trim();
        var timezone = zone(call);

        resolveDate(rawDate, source, timezone).ifPresent(date -> arguments.put("date", date.toString()));
        resolveTime(rawTime.isBlank() ? source : rawTime).ifPresent(time -> arguments.put("time_preference", time.toString()));
        if (!arguments.containsKey("duration_minutes")) arguments.put("duration_minutes", 60);
        if (!arguments.containsKey("timezone")) arguments.put("timezone", timezone.toString());
        return new LlmToolCall(toolCall.id(), toolCall.name(), Map.copyOf(arguments));
    }

    static Optional<LocalDate> resolveDate(String rawDate, String source, ZoneId timezone) {
        for (var value : new String[]{rawDate, source}) {
            if (value == null || value.isBlank()) continue;
            try {
                return Optional.of(LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE));
            } catch (DateTimeParseException ignored) {
                // Continue with natural language and common numeric dates.
            }
            var numeric = Pattern.compile("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})(?!\\d)").matcher(value);
            if (numeric.find()) {
                try {
                    return Optional.of(LocalDate.of(
                            Integer.parseInt(numeric.group(3)),
                            Integer.parseInt(numeric.group(2)),
                            Integer.parseInt(numeric.group(1))
                    ));
                } catch (RuntimeException ignored) {
                    // Let the caller repeat an invalid date.
                }
            }
        }
        var normalized = normalize(source);
        var today = LocalDate.now(timezone);
        if (containsAny(normalized, "tomorrow", "demain")) return Optional.of(today.plusDays(1));
        if (containsAny(normalized, "today", "aujourd'hui", "aujourdhui")) return Optional.of(today);
        for (var entry : WEEKDAYS.entrySet()) {
            if (word(normalized, entry.getKey())) {
                return Optional.of(today.with(TemporalAdjusters.nextOrSame(entry.getValue())));
            }
        }
        return Optional.empty();
    }

    static Optional<LocalTime> resolveTime(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        var normalized = normalize(value).replace(".", "").replaceAll("\\s+", " ").trim();
        if (word(normalized, "midi") || word(normalized, "noon")) return Optional.of(LocalTime.NOON);
        if (word(normalized, "minuit") || word(normalized, "midnight")) return Optional.of(LocalTime.MIDNIGHT);
        var matcher = CLOCK.matcher(normalized);
        while (matcher.find()) {
            var suffix = matcher.group(3) == null ? "" : matcher.group(3).replace(" ", "");
            // A bare number is too ambiguous; a colon or time suffix is required.
            if (matcher.group(2) == null && suffix.isBlank()) continue;
            try {
                var hour = Integer.parseInt(matcher.group(1));
                var minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                if (suffix.startsWith("p") && hour < 12) hour += 12;
                if (suffix.startsWith("a") && hour == 12) hour = 0;
                return Optional.of(LocalTime.of(hour, minute));
            } catch (RuntimeException ignored) {
                // Continue scanning for another usable time expression.
            }
        }
        return Optional.empty();
    }

    private static ZoneId zone(Call call) {
        try {
            return ZoneId.of(call.getAgent().getTimezone());
        } catch (RuntimeException exception) {
            return ZoneId.of("UTC");
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean containsAny(String value, String... candidates) {
        for (var candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private static boolean word(String value, String candidate) {
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(candidate) + "(?![\\p{L}\\p{N}])")
                .matcher(value).find();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'');
    }
}
